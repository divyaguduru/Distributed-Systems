/* Sample skeleton for proxy */

import java.io.*;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * proxy file which can support multiple client
 */
class Proxy {

    private static final int ADD = 1;
    private static final int MINUS = 0;

    private Object lock;
    // predefined varaiable in case of no input
    private String ipString = "127.0.0.1";
    private int serverPort = 11122;
    private String cacheFolder = "ProxyFile";
    private int cacheMaxSize = 100000;
    private Map<String, FileInfo> mainCopy;
    private Map<String, Integer> fileUserCounter;
    private List<FileInfo> lru;
    private int proxyCacheSize;
    public Rpc rpc;

    /**
     * argument array
     * @param args
     */
    public Proxy(String[] args) {
        this.ipString = args[0];
        this.serverPort = Integer.valueOf(args[1]);
        this.cacheFolder = args[2];
        this.cacheMaxSize = Integer.valueOf(args[3]);
        mainCopy = new ConcurrentHashMap<>();
        fileUserCounter = new ConcurrentHashMap<>();
        lru = new CopyOnWriteArrayList<>();
        proxyCacheSize = 0;
        lock = new Object();
        try {
            rpc = (Rpc) Naming.lookup(getRpcAddr());
        } catch (NotBoundException | MalformedURLException | RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     * main method
     * @param args arguments
     * @throws IOException io exception
     */
    public static void main(String[] args) throws IOException {

        Proxy proxy = new Proxy(args);

        (new RPCreceiver(new FileHandlingFactory(proxy))).run();
    }

    private static class FileHandlingFactory implements FileHandlingMaking {
        private Proxy proxy;

        FileHandlingFactory(Proxy proxy) {
            this.proxy = proxy;
        }

        public FileHandling newclient() {
            return new FileHandler(proxy);
        }
    }

    /**
     * file handler class, which handles one open-close session
     */
    public static class FileHandler implements FileHandling {

        private Proxy proxy;
        private static final int READ = 4;
        private static final int READWRITE = 6;
        int fdCounter;
        Map<Integer, FdDetail> fdPool;

        FileHandler(Proxy proxy) {
            this.proxy = proxy;
            fdCounter = 3;
            fdPool = new HashMap<>();
        }

        /**
         * open method
         * @param realName name of server
         * @param o open option
         * @return 0 if success, others if fail
         */
        public int open(String realName, OpenOption o) {
            realName = proxy.shortenName(realName);

            synchronized (proxy.lock) {
                if (!proxy.mainCopy.containsKey(realName)) {
                    proxy.mainCopy.put(realName, new FileInfo(realName, null, 0));
                }

                int ret = proxy.checkServerUpdateCache(realName, o);
                if (ret != 0) {
                    proxy.mainCopy.remove(realName);
                    return ret;
                }

                String randomName = realName;
                if (proxy.mainCopy.containsKey(realName)) {
                    randomName = proxy.mainCopy.get(realName).getRandomName();
                }

                String path = proxy.toProxyPath(randomName);
                File test = new File(path);

                RandomAccessFile file = null;
                int fd = assignFd();
                int originalVersion = proxy.mainCopy.containsKey(realName) ? proxy.mainCopy.get(realName).getVersion() : 0;
                String fileCopyRandomName;

                if (o != OpenOption.READ && o != OpenOption.WRITE &&
                        o != OpenOption.CREATE && o != OpenOption.CREATE_NEW) {
                    return Errors.EINVAL;
                }

                FdDetail fdDetail = null;
                switch (o) {
                    case READ:
                        try {
                            file = new RandomAccessFile(path, "r");
                        } catch (FileNotFoundException e) {
                            System.err.println("Error in executing open, may be trying to open file folder for read");
                        }
                        fdDetail = new FdDetail(file, realName, randomName, originalVersion, READ);
                        proxy.addFileUserCounter(randomName);
                        break;

                    case WRITE:
                        fileCopyRandomName = generateRandomName(realName);
                        try {
                            File fileCopy = new File(proxy.toProxyPath(fileCopyRandomName));
                            int filesize = (int) test.length();
                            proxy.allocateLru(filesize);
                            Files.copy(test.toPath(), fileCopy.toPath());
                            file = new RandomAccessFile(fileCopy, "rw");
                        } catch (FileNotFoundException e) {
                            return Errors.ENOENT;
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        fdDetail = new FdDetail(file, realName, fileCopyRandomName, originalVersion, READWRITE);
                        proxy.addFileUserCounter(fileCopyRandomName); // basically will not be used
                        proxy.updateCacheSize(ADD, fileCopyRandomName);
                        break;

                    case CREATE:
                        fileCopyRandomName = generateRandomName(realName);

                        try {
                            if (proxy.mainCopy.containsKey(realName)) {
                                File fileCopy = new File(proxy.toProxyPath(fileCopyRandomName));
                                int filesize = (int) test.length();
                                proxy.allocateLru(filesize);
                                Files.copy(test.toPath(), fileCopy.toPath());
                                file = new RandomAccessFile(fileCopy, "rw");
                                //TODO bug will left two copy in proxy
                            } else {
                                file = new RandomAccessFile(proxy.toProxyPath(fileCopyRandomName), "rw");
                                FileInfo fileInfo = new FileInfo(realName, fileCopyRandomName, originalVersion);
                                proxy.mainCopy.put(realName, fileInfo);
                                proxy.lru.add(fileInfo);
                            }
                        } catch (IOException e) {
                            System.err.println("Error in create " + realName);
                        }

                        fdDetail = new FdDetail(file, realName, fileCopyRandomName, originalVersion, READWRITE);
                        proxy.addFileUserCounter(fileCopyRandomName);
                        proxy.updateCacheSize(ADD, fileCopyRandomName);
                        break;

                    case CREATE_NEW:
                        fileCopyRandomName = generateRandomName(realName);
                        try {
                            file = new RandomAccessFile(proxy.toProxyPath(fileCopyRandomName), "rw");
                            FileInfo fileInfo = new FileInfo(realName, fileCopyRandomName, originalVersion);
                            proxy.mainCopy.put(realName, fileInfo);
                            proxy.lru.add(fileInfo);
                        } catch (IOException e) {
                            System.err.println("Error in create new " + realName);
                        }
                        fdDetail = new FdDetail(file, realName, fileCopyRandomName, originalVersion, READWRITE);
                        proxy.addFileUserCounter(fileCopyRandomName);
                        proxy.updateCacheSize(ADD, fileCopyRandomName);
                        break;
                }
                fdPool.put(fd, fdDetail);
                proxy.updateLru(realName);
                proxy.printLru();
                return fd;
            }
        }

        /**
         * close method
         * @param fd file descriptor
         * @return 0 is success, others if fail
         */
        public int close(int fd) {
            if (!fdPool.containsKey(fd)) {
                return Errors.EBADF;
            }
            FdDetail fdDetail = fdPool.get(fd);
            int permission = fdDetail.getPermission();
            int originalVersion = fdDetail.getOriginalVersion();
            int latestVersion = proxy.mainCopy.containsKey(fdDetail.getRealName())
                    ? proxy.mainCopy.get(fdDetail.getRealName()).getVersion() : -1;

            proxy.minusFileUserCounter(fdDetail.getRandomName());
            proxy.updateLru(fdDetail.getRealName());
            if (permission == READ) {
                if (latestVersion != originalVersion) {
                    String randomFileName = fdDetail.getRandomName();
                    if (proxy.fileUserCounter.get(randomFileName) == 0) {
                        proxy.deleteRandomFile(randomFileName);
                    }
                }
            } else {
                String randomName = fdDetail.getRandomName();
                proxy.uploadFile(fdDetail.getRealName(), randomName);
                proxy.deleteRandomFile(randomName);
            }
            fdPool.remove(fd);
            return 0;

        }

        /**
         * write file
         * @param fd file descriptor
         * @param buf buf of content to write
         * @return number of bytes which is actually read
         */
        public long write(int fd, byte[] buf) {
            if (!fdPool.containsKey(fd)) {
                return Errors.EBADF;
            }
            File test = new File(proxy.toProxyPath(fdPool.get(fd).getRandomName()));
            if (test.isDirectory()) {
                return Errors.EISDIR;
            }
            if (!test.exists() || !test.canRead() || !test.canWrite()) {
                return Errors.EBADF;
            }
            try {
                fdPool.get(fd).getRaFile().write(buf);
                return buf.length;
            } catch (IOException e) {
                System.err.println("Error in executing write method.");
                return Errors.EBADF;
            }
        }

        /**
         * read method
         * @param fd file descriptor
         * @param buf buf to fill
         * @return number of bytes which are actually readed
         */
        public long read(int fd, byte[] buf) {
            if (!fdPool.containsKey(fd)) {
                return Errors.EBADF;
            }
            File test = new File(proxy.toProxyPath(fdPool.get(fd).getRandomName()));
            if (test.isDirectory()) {
                return Errors.EISDIR;
            }
            if (!test.exists() || !test.canRead()) {
                return Errors.EBADF;
            }
            try {
                long ret = fdPool.get(fd).getRaFile().read(buf);
                return ret == -1 ? 0 : ret;
            } catch (IOException e) {
                System.err.println("Error in executing read method.");
                return Errors.EBADF;
            }
        }

        /**
         * lseek method
         * @param fd file descriptor
         * @param pos pos
         * @param o lseek option
         * @return new position
         */
        public long lseek(int fd, long pos, LseekOption o) {
            if (!fdPool.containsKey(fd)) {
                return Errors.EBADF;
            }
            File test = new File(proxy.toProxyPath(fdPool.get(fd).getRandomName()));
            if (test.isDirectory()) {
                return Errors.EISDIR;
            }
            if (!test.exists() || !test.canRead()) {
                return Errors.EBADF;
            }
            RandomAccessFile file = fdPool.get(fd).getRaFile();
            switch (o) {
                case FROM_START:
                    pos += 0;
                    break;
                case FROM_END:
                    pos += test.length();
                    break;
                case FROM_CURRENT:
                    try {
                        pos += file.getFilePointer();
                    } catch (IOException e) {
                        System.err.println("Error in executing getFilePointer(lseek) method.");
                    }
            }
            try {
                file.seek(pos);
            } catch (IOException e) {
                System.err.println("Error in executing seek(lseek) method.");
            }
            return pos;
        }

        /**
         * unlink method
         * @param realName real name of server
         * @return 0 if success, others if fail
         */
        public int unlink(String realName) {
            realName = proxy.shortenName(realName);
            if (proxy.mainCopy.containsKey(realName)) {
                String randomName = proxy.mainCopy.get(realName).getRandomName();
                if (proxy.fileUserCounter.get(randomName) == 0) {
                    proxy.deleteRandomFile(randomName);
                }
                proxy.mainCopy.remove(realName);
            }
            try {
                return proxy.rpc.unlink(realName);
            } catch (RemoteException e) {
                e.printStackTrace();
                return ErrorCode.REMOTE;
            }
        }

        /**
         * client done method
         */
        public void clientdone() {
            for (Integer fd : fdPool.keySet()) {
                try {
                    File test = new File(proxy.toProxyPath(fdPool.get(fd).getRandomName()));
                    if (test.isFile()) {
                        fdPool.get(fd).getRaFile().close();
                    }
                } catch (IOException e) {
                    System.err.println("Error in executing close(RandomAccessFile) method.");
                }
                fdPool.put(fd, null);
            }
        }

        private synchronized int assignFd() {
            fdCounter++;
            return fdCounter;
        }
    }

    /**
     * get rpc remote file position
     * @return name in string form
     */
    public String getRpcAddr() {
        return "rmi://" + ipString + ":" + serverPort + "/server";
    }

    /**
     * upload file method, which will upload current copy of file to server to update
     * @param realName real name
     * @param uploadFileRandomName upload file's name
     * @return true if success, false if not
     */
    public boolean uploadFile(String realName, String uploadFileRandomName) {
        try {
            File file = new File(toProxyPath(uploadFileRandomName));
            int fileSize = (int) file.length();
            int totalChunks = (fileSize + ChunksTask.CHUNK_SIZE - 1) / ChunksTask.CHUNK_SIZE;
            RandomAccessFile raFile = new RandomAccessFile(file, "r");
            for (int i = 0; i < totalChunks; i++) {
                int offset = i * ChunksTask.CHUNK_SIZE;
                int size = i + 1 == totalChunks ? fileSize - offset : ChunksTask.CHUNK_SIZE;
                byte[] bytes = new byte[size];
                raFile.seek(offset);
                raFile.read(bytes);
                rpc.uploadChunk(realName, bytes, i, totalChunks);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * check server cache
     * @param realName real name
     * @param o open option
     * @return 0 if success or error number
     */
    public int checkServerUpdateCache(String realName, FileHandling.OpenOption o) {
        int cacheVersion = 0; // if no file in cache, version is set to 0
        if (mainCopy.containsKey(realName)) {
            cacheVersion = mainCopy.get(realName).getVersion();
        }

        CheckResult result = null;
        try {
            result = rpc.checkServer(realName, o, cacheVersion);
        } catch (IOException e) {
            e.printStackTrace();
            return ErrorCode.REMOTE;
        }

        if (result.getErrno() == ErrorCode.READ_DIR) {
            String randomName = generateRandomName(realName);
            String randomPath = toProxyPath(randomName);
            new File(randomPath).mkdir();
            FileInfo fileInfo = new FileInfo(realName, randomName, 0);
            mainCopy.put(realName, fileInfo);
            return 0;
        } else if (result.getErrno() == 0 && result.getVersion() == cacheVersion) {
            return 0;
        } else if (result.getErrno() == 0 && result.getVersion() != cacheVersion) {
            String randomName = generateRandomName(realName);
            String randomPath = toProxyPath(randomName);
            int newFileSize = result.getFileContent().length;
            ChunksTask chunksTask = result.getChunksTask();
            if (chunksTask != null) {
                newFileSize = chunksTask.getFileSize();
            }

            allocateLru(newFileSize);

            try {
                // set append to true
                File file = new File(randomPath);
                File parentFolder = file.getParentFile();
                if (!parentFolder.exists()) {
                    parentFolder.mkdirs();
                }
                OutputStream out = new FileOutputStream(file, true);
                out.write(result.getFileContent());

                if (chunksTask != null) {
                    while (chunksTask.hasNextChunk()) {
                        byte[] chunk = chunksTask.getNextChunk(this);
                        out.write(chunk);
                    }
                }
                out.close();
                updateCacheSize(ADD, randomName);
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (mainCopy.containsKey(realName)) {
                FileInfo fileInfo = mainCopy.get(realName);
                int oldVersion = fileInfo.getVersion();
                String oldRandomFile = fileInfo.getRandomName();
                fileInfo.updateCacheInfo(randomName, result.getVersion());
                if (oldRandomFile != null && fileUserCounter.containsKey(oldRandomFile) && fileUserCounter.get(oldRandomFile) == 0) {
                    deleteRandomFile(oldRandomFile);
                }
            } else {
                FileInfo fileInfo = new FileInfo(realName, randomName, result.getVersion());
                mainCopy.put(realName, fileInfo);
                lru.add(fileInfo);
            }

            return 0;
        } else if (result.getErrno() == FileHandling.Errors.ENOENT) {
            if (mainCopy.containsKey(realName)) {
                FileInfo fileInfo = mainCopy.get(realName);
                String randomName = fileInfo.getRandomName();
                if (randomName != null && fileUserCounter.containsKey(randomName) && fileUserCounter.get(randomName) == 0) {
                    deleteRandomFile(randomName);
                }
                mainCopy.remove(realName);
                lru.remove(fileInfo);
            }
            return FileHandling.Errors.ENOENT;
        } else if (result.getErrno() != 0) {
            // other error code
            return result.getErrno();
        } else {
            return 0;
        }
    }

    /**
     * generate random name (add timestamp to the end of file)
     * @param realName real name
     * @return random name
     */
    private static String generateRandomName(String realName) {
        long timeStamp = new Date().getTime();
        return realName + timeStamp;
    }

    /**
     * convert file name to proxy path
     * @param fileName file name
     * @return proxy path of file name
     */
    private String toProxyPath(String fileName) {
        return cacheFolder + "/" + fileName;
    }

    /**
     * add file user counter, user counter is used to record how many user are using this file, counter is added when
     * the file is open
     * @param randomName random name
     * @return current counter value
     */
    private synchronized int addFileUserCounter(String randomName) {
        if (fileUserCounter.containsKey(randomName)) {
            fileUserCounter.put(randomName, fileUserCounter.get(randomName) + 1);
        } else {
            fileUserCounter.put(randomName, 1);
        }
        return fileUserCounter.get(randomName);
    }

    /**
     * minus file user counter, user counter minused when the file is closed
     * @param randomName random name of file
     * @return current counter
     */
    private synchronized int minusFileUserCounter(String randomName) {
        if (!fileUserCounter.containsKey(randomName)) {
            return -1;
        }
        int newCounter = fileUserCounter.get(randomName) - 1;
        if (newCounter < 0) {
            return -1;
        }
        fileUserCounter.put(randomName, newCounter);
        return newCounter;
    }

    /**
     * update LRU to move the freshest one to the end of line
     * @param realName real name
     */
    private void updateLru(String realName) {
        FileInfo fileInfo = mainCopy.get(realName);
        if (fileInfo != null) {
            lru.remove(fileInfo);
            lru.add(fileInfo);
        } else {
            System.err.println(realName + " Error: no record in LRU");
        }
    }

    /**
     * allocate a particular size of space, this method will pick the right file to evict
     * @param allocateSize size to be allocated
     * @return 0 if success
     */
    private synchronized int allocateLru(int allocateSize) {
        //TODO implement it
        int expectedFreeSize = proxyCacheSize + allocateSize - cacheMaxSize;

        int i = 0;
        while (expectedFreeSize > 0 && i < lru.size()) {
            FileInfo fileInfo = lru.get(i);
            String randomName = fileInfo.getRandomName();
            int randomFileUsage = fileUserCounter.containsKey(randomName) ? fileUserCounter.get(randomName) : 0;
            if (randomFileUsage == 0) {
                File file = new File(toProxyPath(fileInfo.getRandomName()));
                if (file.exists()) {
                    int filesize = (int) file.length();
                    deleteRandomFile(fileInfo.getRandomName());
                    lru.remove(fileInfo);
                    mainCopy.remove(fileInfo.getRealName());
                    expectedFreeSize -= filesize;
                } else {
                    System.err.println("Error in LRU evict " + fileInfo.getRandomName() + " does not exist");
                }
            } else {
                i++;
            }
        }
        return 0;
    }

    /**
     * delete random file
     * @param randomFileName random file to be delete
     */
    private void deleteRandomFile(String randomFileName) {
        if (!fileUserCounter.containsKey(randomFileName)) {
            return;
        }
        if (fileUserCounter.get(randomFileName) != 0) {
            return;
        }
        File file = new File(toProxyPath(randomFileName));
        if (file.exists()) {
            updateCacheSize(MINUS, randomFileName);
            file.delete();
        } else {
            System.err.println(randomFileName + " did not exist at all");
        }
    }

    /**
     * update cache size
     * @param option add or minus
     * @param randomName random name of file
     * @return changed size
     */
    private int updateCacheSize(int option, String randomName) {
        File file = new File(toProxyPath(randomName));
        if (file.exists()) {
            int fileSize = (int) file.length();
            if (option == ADD) {
                proxyCacheSize += fileSize;
                return fileSize;
            } else {
                proxyCacheSize -= fileSize;
                return -fileSize;
            }
        } else {
            return -1;
        }
    }

    /**
     * print lru info
     */
    public void printLru() {
        System.err.print("LRU: ");
        for (FileInfo fileInfo : lru) {
            System.err.print(fileInfo.getRealName() + " " + fileUserCounter.get(fileInfo.getRandomName()) + "   ");
        }
        System.err.println();
    }

    /**
     * normalize the relative path
     * @param input input file path
     * @return normalized file path
     */
    private String shortenName(String input) {
        Path path = Paths.get(input);
        return path.normalize().toString();
    }
}

