import java.io.*;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

/**
 * Server class which can handle multiple proxy.
 */
public class Server extends UnicastRemoteObject implements Rpc {
    private static String ROOT_FOLDER;
    private final Map<String, FileInfo> fileIndex;
    private Map<String, String> uploadTaskMap;
    private Set<String> inUseSet;

    /**
     * constructor which use port and root folder
     * @param port port of server number
     * @param rootFolder root folder
     * @throws RemoteException
     */
    protected Server(int port, String rootFolder) throws RemoteException {
        super(port);
        ROOT_FOLDER = rootFolder;
        fileIndex = new HashMap<>();
        uploadTaskMap = new HashMap<>();
        inUseSet = new HashSet<>();
    }

    /**
     * main function
     * @param args input argument
     */
    public static void main(String[] args) {
        int port = Integer.valueOf(args[0]);
        String folder = args[1];

        Server server = null;
        try {
            server = new Server(port, folder);
        } catch (RemoteException e) {
            System.err.println("Error in creating server");
        }
        try {
            LocateRegistry.createRegistry(port);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        try {
            Naming.rebind("rmi://127.0.0.1:" + port + "/server", server);
        } catch (RemoteException | MalformedURLException e) {
            e.printStackTrace();
        }
    }

    /**
     * see the document in {@link Rpc}
     */
    @Override
    public synchronized byte[] downloadChunk(String randomName, int offset, int size) throws RemoteException {

        File file = new File(toServerPath(randomName));

        RandomAccessFile raFile = null;
        try {
            raFile = new RandomAccessFile(file, "r");
        } catch (FileNotFoundException e) {
            System.err.println("Error in download chunk, file does not exist");
            return null;
        }

        byte[] bytes = new byte[size];
        try {
            raFile.seek(offset);
            raFile.read(bytes);

            int fileSize = (int) raFile.length();
            if (offset + size >= fileSize) {
                inUseSet.remove(randomName);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return bytes;
    }

    /**
     * see the document in {@link Rpc}
     */
    @Override
    public int uploadChunk(String realName, byte[] fileContent, int chunk, int totalChunk) throws RemoteException {

        String randomName = null;
        if (chunk == 0) {
            randomName = generateRandomName(realName);
            uploadTaskMap.put(realName, randomName);
        }
        randomName = uploadTaskMap.get(realName);
        File file = new File(toServerPath(randomName));
        try {
            OutputStream out = new FileOutputStream(file, true);
            out.write(fileContent);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (chunk + 1 == totalChunk) {
            synchronized (fileIndex) {
                updateFileIndex(realName, randomName);
            }
        }
        return 0;
    }

    /**
     * see the document in {@link Rpc}
     */
    @Override
    public synchronized CheckResult checkServer(String realName, FileHandling.OpenOption o, int version) throws RemoteException {

        int testOpenRet = testOpen(realName, o);
        if (testOpenRet != 0) {
            return new CheckResult(testOpenRet);
        }

        String randomName = getRandomName(realName);
        if (randomName == null) {
            return new CheckResult(FileHandling.Errors.ENOENT);
        }
        int serverVersion = fileIndex.get(realName).getVersion();
        if (serverVersion == version) {
            return new CheckResult(realName, version);
        } else {
            CheckResult result = new CheckResult(realName, serverVersion);
            File randomFile = new File(toServerPath(randomName));
            if (randomFile.isDirectory()) {
                return new CheckResult(ErrorCode.READ_DIR);
            }
            try {
                inUseSet.add(randomName);
                int fileSize = (int) randomFile.length();
                int bufferSize = fileSize > ChunksTask.CHUNK_SIZE ? ChunksTask.CHUNK_SIZE : fileSize;
                byte[] fileContent = new byte[bufferSize];
                InputStream in = new FileInputStream(randomFile);
                in.read(fileContent);
                result.setFileContent(fileContent);
                if (fileSize > ChunksTask.CHUNK_SIZE) {
                    result.setChunksTask(new ChunksTask(randomName));
                } else {
                    inUseSet.remove(randomName);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return result;
        }
    }

    /**
     * test open function
     * @param realName real name of file
     * @param o open function
     * @return 0 if success, others if error
     */
    private int testOpen(String realName, FileHandling.OpenOption o) {
        if (!inServerFolder(realName)) {
            return FileHandling.Errors.EPERM;
        }
        loadFile(realName);
        String randomName = getRandomName(realName) != null ? getRandomName(realName) : generateRandomName(realName);
        String path = toServerPath(randomName);
        File test = new File(path);
        switch (o) {
            case READ:
                if (!test.exists()) {
                    return FileHandling.Errors.ENOENT;
                }
                if (!test.canRead()) {
                    return FileHandling.Errors.EPERM;
                }
                return 0;
            case WRITE:
                if (!test.exists()) {
                    return FileHandling.Errors.ENOENT;
                }
                if (!test.canRead() || !test.canWrite()) {
                    return FileHandling.Errors.EPERM;
                }
                if (test.isDirectory()) {
                    return FileHandling.Errors.EISDIR;
                }

                return 0;

            case CREATE:
                if (test.exists() && (!test.canRead() || !test.canWrite())) {
                    return FileHandling.Errors.EPERM;
                }
                if (test.isDirectory()) {
                    return FileHandling.Errors.EISDIR;
                }
                if (!test.exists()) {
                    updateFileIndex(realName, randomName);
                    File parentFolder = test.getParentFile();
                    if (!parentFolder.exists()) {
                        parentFolder.mkdirs();
                    }
                    try {
                        test.createNewFile();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
                return 0;

            case CREATE_NEW:
                if (test.exists()) {
                    return FileHandling.Errors.EEXIST;
                }
                return testOpen(realName, FileHandling.OpenOption.CREATE);
            default:
                return FileHandling.Errors.EINVAL;
        }
    }

    /**
     * see the document in {@link Rpc}
     */
    @Override
    public synchronized int unlink(String realName) throws RemoteException {

        if (!inServerFolder(realName)) {
            return FileHandling.Errors.ENOENT;
        }

        String randomName = getRandomName(realName);
        if (randomName == null) {
            return FileHandling.Errors.ENOENT;
        }
        String randomPath = toServerPath(randomName);
        File file = new File(randomPath);
        while (inUseSet.contains(randomName)) {
        }

        randomName = getRandomName(realName);
        if (randomName == null) {
            return FileHandling.Errors.ENOENT;
        }

        file.delete();
        fileIndex.remove(realName);
        return 0;
    }

    /**
     * change file name to file path
     * @param fileName name of file
     * @return path in String form
     */
    public static String toServerPath(String fileName) {
        return ROOT_FOLDER + "/" + fileName;
    }

    /**
     * get random name from record
     * @param realName real name of server file
     * @return random name
     */
    private String getRandomName(String realName) {
        loadFile(realName);
        if (fileIndex.containsKey(realName)) {
            return fileIndex.get(realName).getRandomName();
        } else {
            return null; // no such file
        }
    }

    /**
     * generate random number
     * @param realName real name of server
     * @return generated random name
     */
    private static String generateRandomName(String realName) {
        long timeStamp = new Date().getTime();
        return realName + timeStamp;
    }

    /**
     * load file to server record. When server is started, there is no its content info in the server.
     * @param realName real name of file
     */
    private void loadFile(String realName) {
        File file = new File(toServerPath(realName));
        if (!fileIndex.containsKey(realName) && file.exists()) {
            fileIndex.put(realName, new FileInfo(realName, realName, 1));
        }
    }

    /**
     * update file index
     * @param realName real name of server
     * @param newRandomName new random name
     * @return new random name
     */
    private synchronized String updateFileIndex(String realName, String newRandomName) {
        if (fileIndex.containsKey(realName)) {
            FileInfo fileInfo = fileIndex.get(realName);
            fileInfo.updateRandomName(newRandomName);
            fileInfo.updateVersion();
            return newRandomName;
        } else {
            fileIndex.put(realName, new FileInfo(realName, newRandomName, 1));
            return null;
        }
    }

    /**
     * decide whether in the range of root folder
     * @param inputName name of input file name
     * @return true if in root folder of its subfolder; false if not
     */
    private boolean inServerFolder(String inputName) {
        File fileFolder = new File(ROOT_FOLDER);
        Path fileFolderAbsPath = fileFolder.toPath().toAbsolutePath().normalize();
        File input = new File(toServerPath(inputName));
        Path inputAbsPath = input.toPath().toAbsolutePath().normalize();
        return inputAbsPath.startsWith(fileFolderAbsPath);
    }
}