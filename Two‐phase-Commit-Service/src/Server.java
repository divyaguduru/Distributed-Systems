import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Server Class
 * <p>
 * This class is the implementation of 2PC server. All the server operation information is stored in the four java Map.
 * <p>
 * tasks: a map which stores all tasks
 * <p>
 * taskInVoting: a map which stores all ongoing voting tasks.
 * <p>
 * taskInExecuting: a map which stores all ongoing executing tasks.
 * <p>
 * taskInAborting: a map which stores all ongoing aborting tasks.
 * <p>
 * Basically tasks belongs to one of the four tasks (voting, executing, aborting, finished). The first three are stored
 * in both "tasks" map and one of the three map above. Finished tasks will archived in "tasks" map.
 */
public class Server implements ProjectLib.CommitServing {
    static String ServerId = "Server";
    private static ProjectLib PL;
    private static Logger logger;

    // <filename, list<addr>>
    private static Map<String, List<String>> tasks = new ConcurrentHashMap<>();
    private static Map<String, List<String>> tasksInVoting = new ConcurrentHashMap<>();
    private static Map<String, List<String>> tasksInExecuting = new ConcurrentHashMap<>();
    private static Map<String, List<String>> tasksInAborting = new ConcurrentHashMap<>();

    /**
     * Non-block commit service used by external client. It will start a new thread to handle request. First it will
     * send vote request to every related nodes and collect responses. If any one of the above nodes denied to commit
     * or any message is timeout, server will abort this commit. Otherwise, it will approve it and start executing it.
     * <p>
     * Both abort and execution are done with another new thread.
     *
     * @param filename name of collage to be committed
     * @param img      img bytes
     * @param sources  source information
     */
    public void startCommit(String filename, byte[] img, String[] sources) {

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String compositeFilename = "composites/" + filename;

                    List<String> addrs = getAddrs(sources);
                    tasks.put(compositeFilename, addrs);
                    logger.logMap("tasks", tasks);
                    tasksInVoting.put(compositeFilename, new CopyOnWriteArrayList<>());
                    logger.logMap("tasksInVoting", tasksInVoting);
                    backupImage(filename, img);

                    List<ProjectLib.Message> addrMessageMap = encodeVoteMessages(compositeFilename, img, sources);
                    for (ProjectLib.Message message : addrMessageMap) {
                        PL.sendMessage(message);
                    }

                    for (int i = 0; i < 17; i++) {
                        Thread.sleep(200);
                        if (tasksInExecuting.containsKey(compositeFilename)) {
                            execute(compositeFilename);
                            break;
                        } else if (tasksInAborting.containsKey(compositeFilename)) {
                            abort(compositeFilename);
                            break;
                        } else if (i == 16) {
                            if (tasksInExecuting.containsKey(compositeFilename)) {
                                execute(compositeFilename);
                            } else {
                                voteToAbort(compositeFilename);
                                abort(compositeFilename);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * Main method of server. It will initialize its ProjectLib object, Logger object and try to restore previous state
     * from log file (if log exist).
     *
     * @param args arguments
     * @throws Exception
     */
    public static void main(String args[]) throws Exception {
        if (args.length != 1) throw new Exception("Need 1 arg: <port>");
        Server srv = new Server();
        PL = new ProjectLib(Integer.parseInt(args[0]), srv);
        logger = new Logger(ServerId, PL);
        restoreFromLog();

        // main loop
        while (true) {
            ProjectLib.Message msg = PL.getMessage();

            ParsedMessage parsedMessage = new ParsedMessage(msg);

            if (parsedMessage.code == MessageBuilder.VOTE_RESPONSE) {
                handleVote(parsedMessage);
            } else if (parsedMessage.code == MessageBuilder.ACK) {
                handleAck(parsedMessage);
            }
        }
    }

    /**
     * Handle vote messages.
     * <p>
     * It will collect vote responses from nodes and record results. If all nodes agree to commit,
     * it will transfer task from "voting" state to "executing" state. If not, it will be treated as "aborting".
     *
     * @param parsedMessage parsed message from message byte array
     */
    private static void handleVote(ParsedMessage parsedMessage) {
        String compositeFileName = parsedMessage.compositeFileName;
        String nodeId = parsedMessage.nodeId;
        if (tasksInVoting.containsKey(compositeFileName)) {
            if (parsedMessage.voteResult) {
                List<String> voteList = tasksInVoting.get(compositeFileName);
                if (!voteList.contains(nodeId)) {
                    voteList.add(nodeId);
                    logger.logMap("tasksInVoting", tasksInVoting);
                }
                if (toExecute(compositeFileName)) {
                    voteToExec(compositeFileName);
                }
            } else {
                voteToAbort(compositeFileName);
            }
        }
    }

    /**
     * Handle ack.
     * <p>
     * It collects ack from nodes and put it into "taskInAborting" and "tasksInExecuting" map.
     *
     * @param parsedMessage parsed messages from byte message
     */
    private static void handleAck(ParsedMessage parsedMessage) {
        String compositeFileName = parsedMessage.compositeFileName;
        String nodeId = parsedMessage.nodeId;
        if (tasksInAborting.containsKey(compositeFileName)) {
            List<String> ackList = tasksInAborting.get(compositeFileName);
            if (!ackList.contains(nodeId)) {
                ackList.add(nodeId);
            }
        } else if (tasksInExecuting.containsKey(compositeFileName)) {
            List<String> ackList = tasksInExecuting.get(compositeFileName);
            if (!ackList.contains(nodeId)) {
                ackList.add(nodeId);
            }
        }
    }

    /**
     * transfer task from vote state to exec state.
     *
     * @param compositeFileName file name
     */
    private static void voteToExec(String compositeFileName) {
        if (!tasksInExecuting.containsKey(compositeFileName)) {
            tasksInExecuting.put(compositeFileName, new CopyOnWriteArrayList<>());
            logger.logMap("tasksInExecuting", tasksInExecuting);
        }
        tasksInVoting.remove(compositeFileName);
        logger.logMap("tasksInVoting", tasksInVoting);
    }

    /**
     * transfer task from vote state to abort state.
     *
     * @param compositeFileName file name
     */
    private static void voteToAbort(String compositeFileName) {
        if (!tasksInAborting.containsKey(compositeFileName)) {
            tasksInAborting.put(compositeFileName, new CopyOnWriteArrayList<>());
            logger.logMap("tasksInAborting", tasksInAborting);
        }
        tasksInVoting.remove(compositeFileName);
        logger.logMap("tasksInVoting", tasksInVoting);
    }

    /**
     * check if a task can be executed
     *
     * @param compositeFileName file name
     */
    private static boolean toExecute(String compositeFileName) {
        if (!tasksInVoting.containsKey(compositeFileName)) {
            return false;
        }
        return tasks.get(compositeFileName).size() == tasksInVoting.get(compositeFileName).size();
    }

    /**
     * check if an aborting task is finished
     *
     * @param compositeFileName file name
     */
    private static boolean abortFinished(String compositeFileName) {
        if (!tasksInAborting.containsKey(compositeFileName)) {
            return true;
        }
        return tasks.get(compositeFileName).size() == tasksInAborting.get(compositeFileName).size();
    }

    /**
     * check if an executing task is finished
     *
     * @param compositeFileName file name
     */
    private static boolean executeFinished(String compositeFileName) {
        if (!tasksInExecuting.containsKey(compositeFileName)) {
            return true;
        }
        return tasks.get(compositeFileName).size() == tasksInExecuting.get(compositeFileName).size();
    }

    /**
     * When an aborting job is finished, remove it from "aborting" map and log new map
     *
     * @param compositeFileName file name
     */
    private static void commitAbort(String compositeFileName) {
        tasksInAborting.remove(compositeFileName);
        logger.logMap("tasksInAborting", tasksInAborting);
    }

    /**
     * When an executing job is finished, remove it from "executing" map and log new map
     *
     * @param compositeFileName file name
     */
    private static void commitExecute(String compositeFileName) {
        tasksInExecuting.remove(compositeFileName);
        logger.logMap("tasksInExecuting", tasksInExecuting);
    }

    /**
     * When a job is successfully committed, restored the cache file to its original name.
     *
     * @param compositeFileName file name
     * @throws IOException exception may be thrown during file renaming
     */
    private static void restoreBackupImage(String compositeFileName) throws IOException {
        // rename the file back to its original name
        String filename = compositeFileName.split("/", 2)[1]; // limit split result to 2
        Path source = Paths.get(filename + "backup");
        if (Files.exists(source)) {
            Files.move(source, source.resolveSibling(filename), StandardCopyOption.REPLACE_EXISTING);
        }
        PL.fsync();
    }

    /**
     * Execute the task. This method is called after all nodes have approved to commit. It will start a new thread to
     * handle executing and will check the execution status periodically.
     *
     * @param compositeFileName file name
     */
    private static void execute(String compositeFileName) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!tasksInExecuting.containsKey(compositeFileName)) {
                        commitExecute(compositeFileName);
                        return;
                    }

                    restoreBackupImage(compositeFileName);

                    List<ProjectLib.Message> messages = encodeExecMsg(compositeFileName);
                    for (ProjectLib.Message m : messages) {
                        PL.sendMessage(m);
                    }

                    List<String> ackList = tasksInExecuting.get(compositeFileName);
                    while (true) {
                        Thread.sleep(300);

                        if (executeFinished(compositeFileName)) {
                            commitExecute(compositeFileName);
                            break;
                        } else {
                            for (ProjectLib.Message m : messages) {
                                if (!ackList.contains(m.addr)) {
                                    PL.sendMessage(m);
                                }
                            }
                        }
                    }
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * Execute the task. This method is called after server has decided to abort this mission. It will start a new thread to
     * handle abort and will check the abort status periodically.
     *
     * @param compositeFileName file name
     */
    private static void abort(String compositeFileName) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String filename = compositeFileName.split("/", 2)[1]; // limit split result to 2
                    deleteBackupImage(filename);
                    if (!tasksInAborting.containsKey(compositeFileName)) {
                        commitAbort(compositeFileName);
                        return;
                    }

                    List<ProjectLib.Message> messages = encodeAbortMsg(compositeFileName);
                    for (ProjectLib.Message m : messages) {
                        PL.sendMessage(m);
                    }

                    List<String> ackList = tasksInAborting.get(compositeFileName);
                    while (true) {
                        Thread.sleep(300);

                        if (abortFinished(compositeFileName)) {
                            commitAbort(compositeFileName);
                            break;
                        } else {
                            for (ProjectLib.Message m : messages) {
                                if (!ackList.contains(m.addr)) {
                                    PL.sendMessage(m);
                                }
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * encode vote messages.
     *
     * @param filename file name
     * @param img      img bytes
     * @param sources  sources information
     * @return a list of messages
     */
    private static List<ProjectLib.Message> encodeVoteMessages(String filename, byte[] img, String[] sources) {
        Map<String, List<String>> addrImageMap = sourcesToAddrMap(sources);

        List<ProjectLib.Message> messages = new ArrayList<>();
        for (String addr : addrImageMap.keySet()) {
            String messageString = MessageBuilder.VOTE + "!" + filename + "@";
            String imageString = "";
            for (String image : addrImageMap.get(addr)) {
                if (imageString.length() != 0) {
                    imageString += "#";
                }
                imageString += image;
            }
            messageString = messageString + imageString;
            messages.add(MessageBuilder.build(addr, messageString, img));
        }
        return messages;
    }

    /**
     * encode execution message
     *
     * @param compositeFilename file name
     * @return a list of messages
     */
    private static List<ProjectLib.Message> encodeExecMsg(String compositeFilename) {
        List<String> addrs = tasks.get(compositeFilename);

        List<ProjectLib.Message> execMsgs = new ArrayList<>();
        for (String addr : addrs) {
            String messageString = MessageBuilder.EXEC + "!" + compositeFilename;
            execMsgs.add(MessageBuilder.build(addr, messageString, null));
        }
        return execMsgs;
    }

    /**
     * encode abort message
     *
     * @param compositeFilename file name
     * @return a list of messages
     */
    private static List<ProjectLib.Message> encodeAbortMsg(String compositeFilename) {
        List<String> addrs = tasks.get(compositeFilename);

        List<ProjectLib.Message> execMsgs = new ArrayList<>();
        for (String addr : addrs) {
            String messageString = MessageBuilder.ABORT + "!" + compositeFilename;
            execMsgs.add(MessageBuilder.build(addr, messageString, null));
        }
        return execMsgs;
    }

    /**
     * encode source to address map
     *
     * @param sources sources information in array form
     * @return a java map structure, key-address, value-List of composed file
     */
    private static Map<String, List<String>> sourcesToAddrMap(String[] sources) {
        Map<String, List<String>> addrImageMap = new HashMap<>();
        for (String s : sources) {
            String[] addrImage = s.split(":");
            String addr = addrImage[0];
            String image = addrImage[1];
            if (!addrImageMap.containsKey(addr)) {
                addrImageMap.put(addr, new CopyOnWriteArrayList<>());
            }
            addrImageMap.get(addr).add(image);
        }
        return addrImageMap;
    }

    /**
     * get all address from source information in array form
     *
     * @param sources sources information
     * @return list of address
     */
    private static List<String> getAddrs(String[] sources) {
        Set<String> addrSet = new HashSet<>();
        for (String source : sources) {
            addrSet.add(source.split(":")[0]);
        }
        return new CopyOnWriteArrayList<>(addrSet);
    }

    /**
     * Store file in disc with a backup name. This file will be change back to its original name after approved commit.
     * Or it will be deleted if server decides to abort this mission.
     *
     * @param filename file name
     * @param img      image bytes
     */
    private static void backupImage(String filename, byte[] img) {
        String backupName = filename + "backup";
        try {
            OutputStream out = new FileOutputStream(new File(backupName));
            out.write(img);
            PL.fsync();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * delete backup image. This will happen when the task is aborted.
     *
     * @param filename file name
     */
    private static void deleteBackupImage(String filename) {
        String backupName = filename + "backup";
        File file = new File(backupName);
        if (file.exists()) {
            file.delete();
        }
        PL.fsync();
    }

    /**
     * restore previous state from log file. It will read log file line by line and get the lastest log information
     * on the four map and pop it back to memory.
     */
    private static void restoreFromLog() {
        logger.serverLoadLog(tasks, tasksInVoting, tasksInExecuting, tasksInAborting);

        for (String compositeFilename : tasksInVoting.keySet()) {
            if (!tasksInExecuting.containsKey(compositeFilename)) {
                tasksInAborting.put(compositeFilename, new CopyOnWriteArrayList<>());
            }
        }
        tasksInVoting.clear();
        logger.logMap("tasksInAborting", tasksInAborting);
        logger.logMap("tasksInVoting", tasksInVoting);

        for (String compositeFilename : tasksInExecuting.keySet()) {
            execute(compositeFilename);
        }

        for (String compositeFilename : tasksInAborting.keySet()) {
            abort(compositeFilename);
        }
    }
}
