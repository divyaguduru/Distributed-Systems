
import java.io.File;
import java.io.Serializable;
import java.rmi.RemoteException;

/**
 * This class is used for download huge file. It shore the randomName(actual copy of files on server), file size and
 * total chunks.
 */
public class ChunksTask implements Serializable {

    public static final int CHUNK_SIZE = 1000003;

    private final String serverRandomName;
    private final int fileSize;
    private final int totalChunks;
    private int nextChunk;

    /**
     * constructor
     * @param randomName name of random file
     */
    public ChunksTask(String randomName) {
        this.serverRandomName = randomName;
        File file = new File(Server.toServerPath(randomName));
        this.fileSize = (int) file.length();
        this.totalChunks = (fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE;
        this.nextChunk = 1;
    }

    /**
     * return file name on server
     * @return file's name on server
     */
    public String getServerRandomName() {
        return serverRandomName;
    }

    /**
     * get file size
     * @return size of file
     */
    public int getFileSize() {
        return fileSize;
    }

    /**
     * check if there is following chunk
     * @return true if there is following job, false if not
     */
    public boolean hasNextChunk() {
        if (nextChunk < totalChunks) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * get next chunk
     * @param proxy proxy
     * @return byte array
     */
    public byte[] getNextChunk(Proxy proxy) {
        if (!hasNextChunk()) {
            System.err.println("Error: no further chunks");
            return null;
        }
        int size = CHUNK_SIZE;
        if (nextChunk == totalChunks - 1) {
            size = fileSize - nextChunk * CHUNK_SIZE;
        }
        int offset = nextChunk * CHUNK_SIZE;
        nextChunk++;

        byte[] bytes = null;
        try {
            bytes = proxy.rpc.downloadChunk(serverRandomName, offset, size);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return bytes;
    }
}
