import java.io.File;
import java.util.*;

/**
 * UserNode class. This class performs the roles to decide whether a picture will be approved or not.
 */
public class UserNode implements ProjectLib.MessageHandling {
    private static List<String> pending = new ArrayList<>();
    private static Map<String, List<String>> tasks = new HashMap<>();

    private static ProjectLib PL;
    private static Logger logger;
    private static String myId;

    public UserNode(String id) {
        myId = id;
    }

    /**
     * Deliver message.
     *
     * @param msg message received.
     * @return always true, all message will be handle immediately without putting it to node queue.
     */
    @Override
    public boolean deliverMessage(ProjectLib.Message msg) {
        ParsedMessage parsedMessage = new ParsedMessage(msg);

        if (parsedMessage.code == MessageBuilder.VOTE) {
            boolean voteYes = vote(parsedMessage.img, parsedMessage.imageNames);

            List<String> imageNames = Arrays.asList(parsedMessage.imageNames);
            tasks.put(parsedMessage.compositeFileName, imageNames);
            logger.logMap("tasks", tasks);
            if (voteYes) {
                pending.addAll(imageNames);
                logger.logList("pending", pending);

            }
            sendVoteResponse(parsedMessage.compositeFileName, voteYes);
        } else if (parsedMessage.code == MessageBuilder.EXEC) {
            execute(parsedMessage.compositeFileName);
        } else if (parsedMessage.code == MessageBuilder.ABORT) {
            abort(parsedMessage.compositeFileName);
        }
        return true;
    }

    /**
     * method to execute the task (remove file locally)
     *
     * @param compositeFileName file name
     */
    private static void execute(String compositeFileName) {
        List<String> imageNames = tasks.get(compositeFileName);
        for (String imageName : imageNames) {
            File file = new File(imageName);
            if (file.exists()) {
                file.delete();
            }
            PL.fsync();
        }
        sendAck(compositeFileName);
    }

    /**
     * method to abort the task
     *
     * @param compositeFileName file name
     */
    private static void abort(String compositeFileName) {
        List<String> imageNames = tasks.get(compositeFileName);
        for (String imageName : imageNames) {
            if (pending.contains(imageName)) {
                pending.remove(imageName);
                logger.logList("pending", pending);
            }
        }
        sendAck(compositeFileName);
    }

    /**
     * Give feedback on commit. It will first check the existence of file. If not, this method return false directly.
     * If exist, it will further use askUser method to decide
     *
     * @param image      image bytes of collage
     * @param imageNames an array of related image
     * @return true on approval, false on denied.
     */
    private boolean vote(byte[] image, String[] imageNames) {
        for (String imageName : imageNames) {
            if (!inFolder(imageName) || pending.contains(imageName)) {
                return false;
            }
        }
        return PL.askUser(image, imageNames);
    }

    /**
     * helper method to check the existence of local file
     *
     * @param fileName name of file
     * @return true if file exist, false if not
     */
    private boolean inFolder(String fileName) {
        File file = new File(fileName);
        return file.exists();
    }

    /**
     * Send vote response back to server
     *
     * @param filename   name of file
     * @param voteResult result of
     */
    private void sendVoteResponse(String filename, boolean voteResult) {
        String messageString = MessageBuilder.VOTE_RESPONSE + "!" + filename + "@" + myId + "#" + (voteResult ? 1 : 0);
        ProjectLib.Message message = MessageBuilder.build(Server.ServerId, messageString, null);
        PL.sendMessage(message);
    }

    /**
     * Send ack response to server
     *
     * @param filename file name
     */
    private static void sendAck(String filename) {
        String messageString = MessageBuilder.ACK + "!" + filename + "@" + myId;
        ProjectLib.Message message = MessageBuilder.build(Server.ServerId, messageString, null);
        PL.sendMessage(message);
    }

    /**
     * restore state from log file
     */
    private static void restoreFromLog() {
        logger.nodeLoadLog(pending, tasks);
    }

    /**
     * main method of Node. It will initialize its ProjectLib object, Logger object and try to restore previous state
     * from log file (if log exist).
     *
     * @param args arguments
     * @throws Exception
     */
    public static void main(String args[]) throws Exception {
        if (args.length != 2) throw new Exception("Need 2 args: <port> <id>");
        UserNode UN = new UserNode(args[1]);
        PL = new ProjectLib(Integer.parseInt(args[0]), args[1], UN);
        logger = new Logger(args[1], PL);
        restoreFromLog();
    }
}
