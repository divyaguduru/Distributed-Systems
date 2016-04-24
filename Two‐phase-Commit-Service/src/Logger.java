import java.io.*;
import java.util.*;

/**
 * Logger class which provides method to log list/map into log file. An auto-flush PrintWriter is created by its
 * constructor and ProjectLib allows log list/map method can automatically synchronize folder.
 *
 * @author guangyu
 * @version 1.0
 * @since 4/20/16
 */
public class Logger {
    private File logFile;
    private PrintWriter writer;
    private ProjectLib PL;

    /**
     * constructor of Logger class
     *
     * @param id id of server/node
     * @param PL ProjectLib Class used to synchronize folders
     */
    Logger(String id, ProjectLib PL) {
        this.writer = writerFactory(id);
        this.PL = PL;
        this.logFile = new File("log" + id);
    }

    /**
     * log map into log file
     *
     * @param mapName name of map to be logged
     * @param record  map to be logged
     */
    void logMap(String mapName, Map<String, List<String>> record) {
        String result = mapList2String(record);
        writer.println(mapName + "\t" + result);
        PL.fsync();
    }

    /**
     * log list into list file
     *
     * @param listName name of list to be logged
     * @param record   list to be logged
     */
    void logList(String listName, List<String> record) {
        String result = list2String(record);
        writer.println(listName + "\t" + result);
        PL.fsync();
    }

    /**
     * Restore server state from log file. Four map objects will be provided as parameters and this method will pop the map
     * with latest log.
     *
     * @param tasks            tasks map
     * @param tasksInVoting    tasksInVoting map
     * @param tasksInExecuting tasksInExecuting map
     * @param tasksInAborting  tasksInAborting map
     */
    void serverLoadLog(
            Map<String, List<String>> tasks,
            Map<String, List<String>> tasksInVoting,
            Map<String, List<String>> tasksInExecuting,
            Map<String, List<String>> tasksInAborting) {
        String tasksLog = "";
        String tasksInVotingLog = "";
        String tasksInExecutingLog = "";
        String tasksInAbortingLog = "";
        try {
            BufferedReader reader = new BufferedReader(new FileReader(logFile));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] keyValue = line.split("\t");
                if (keyValue.length == 2) {
                    if (line.startsWith("tasksInAborting")) {
                        tasksInAbortingLog = keyValue[1];
                    } else if (line.startsWith("tasksInExecuting")) {
                        tasksInExecutingLog = keyValue[1];
                    } else if (line.startsWith("tasksInVoting")) {
                        tasksInVotingLog = keyValue[1];
                    } else if (line.startsWith("tasks")) {
                        tasksLog = keyValue[1];
                    }
                }
            }
            tasks.putAll(string2MapList(tasksLog));
            tasksInVoting.putAll(string2MapList(tasksInVotingLog));
            tasksInExecuting.putAll(string2MapList(tasksInExecutingLog));
            tasksInAborting.putAll(string2MapList(tasksInAbortingLog));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Restore node state from log file. Pending file list and tasks map object will be provided as parameters and this
     * method will pop the map and list with latest log.
     *
     * @param pending pending image list
     * @param tasks   tasks map
     */
    void nodeLoadLog(List<String> pending, Map<String, List<String>> tasks) {
        String pendingLog = "";
        String tasksLog = "";
        try {
            BufferedReader reader = new BufferedReader(new FileReader(logFile));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] keyValue = line.split("\t");
                if (keyValue.length == 2) {
                    if (line.startsWith("pending")) {
                        pendingLog = keyValue[1];
                    } else if (line.startsWith("tasks")) {
                        tasksLog = keyValue[1];
                    }
                }
            }
            pending.addAll(string2List(pendingLog));
            tasks.putAll(string2MapList(tasksLog));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * helper method to convert a map into string
     *
     * @param input input map
     * @return string format map
     */
    private static String mapList2String(Map<String, List<String>> input) {
        StringBuilder output = new StringBuilder();
        for (String key : input.keySet()) {
            String valueString = list2String(input.get(key));
            if (output.length() != 0) {
                output.append("!");
            }
            output.append(key).append("@").append(valueString);
        }
        return output.toString();
    }

    /**
     * helper method to convert list to string.
     *
     * @param input input list
     * @return string format list
     */
    private static String list2String(List<String> input) {
        StringBuilder valueString = new StringBuilder();
        for (String value : input) {
            if (valueString.length() != 0) {
                valueString.append("#");
            }
            valueString.append(value);
        }
        return valueString.toString();
    }

    /**
     * helper method to restore a map from a string
     *
     * @param input input string
     * @return map restored
     */
    private static Map<String, List<String>> string2MapList(String input) {
        Map<String, List<String>> output = new HashMap<>();
        if (input.length() == 0) {
            return output;
        }
        String[] keyValues = input.split("!");
        for (String kv : keyValues) {
            String[] kvSplit = kv.split("@");

            List<String> valueList = new ArrayList<>();
            if (kvSplit.length == 2) {
                valueList = string2List(kvSplit[1]);
            }
            output.put(kvSplit[0], valueList);

        }
        return output;
    }

    /**
     * helper method to restore a list from a string
     *
     * @param input input string
     * @return list record
     */
    private static List<String> string2List(String input) {
        return Arrays.asList(input.split("#"));
    }

    /**
     * helper method to create auto-flash and auto-append PrintWriter. Log is name as log{id}
     *
     * @param id id of server/node
     * @return an auto-flash and auto-append PrintWriter
     */
    private static PrintWriter writerFactory(String id) {
        String fileName = "log" + id;
        try {
            return new PrintWriter(new FileWriter(fileName, true), true);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
