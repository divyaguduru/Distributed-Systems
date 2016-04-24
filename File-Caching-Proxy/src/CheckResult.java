import java.io.Serializable;

/**
 * class used to check cache freshness and download file
 * <p>
 * The client will send its current version number to server, if version number is behind the server, server will
 * send its current copy to client. If the file is small enough, the file can be send by one pass. If the file is
 * larger, client will call download client to download further chunks
 */
public class CheckResult implements Serializable {
    private final String realName;
    private final int version;
    private int errno;
    private byte[] fileContent;
    private ChunksTask chunksTask;

    /**
     * constructor 1
     * In normal case (without error), the server will send the file number and version number.
     * @param realName real name of file
     * @param version current version
     */
    CheckResult(String realName, int version) {
        this.realName = realName;
        this.version = version;
        this.errno = 0;
        this.fileContent = new byte[0];
        this.chunksTask = null;
    }

    /**
     * constructor 2
     * @param errno when there is error in server, the server will send error number directly
     */
    CheckResult(int errno) {
        this.realName = null;
        this.version = 0;
        this.errno = errno;
        this.fileContent = new byte[0];
    }

    /**
     * get real number of file
     * @return name of file
     */
    public String getRealName() {
        return realName;
    }

    /**
     * get version number
     * @return version number
     */
    public int getVersion() {
        return version;
    }

    /**
     * get errno number
     * @return errno number
     */
    public int getErrno() {
        return errno;
    }

    /**
     * download file content
     * @return byte array of content
     */
    public byte[] getFileContent() {
        return fileContent;
    }

    /**
     * get chunk task
     * @return chunk task
     */
    public ChunksTask getChunksTask() {
        return chunksTask;
    }

    /**
     * set file content in this result.
     * small file can be stored in this class
     * @param fileContent byte array of file content
     */
    public void setFileContent(byte[] fileContent) {
        this.fileContent = fileContent;
    }

    /**
     * set chunk task, if the file is bigger enough. It needs further download chunks, chunks information is stored in
     * chunksTask
     *
     * @param chunksTask chunk task to set
     */
    public void setChunksTask(ChunksTask chunksTask) {
        this.chunksTask = chunksTask;
    }
}
