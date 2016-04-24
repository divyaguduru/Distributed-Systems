import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * RPC interface which defines remote operation
 */
public interface Rpc extends Remote {
    /**
     * upload chunk to server, the chunk will append to the end of current file
     * @param realName real name of file
     * @param fileContent byte array of file content
     * @param chunk chunk index
     * @param totalChunk total chunk number
     * @return 0 is success or error number
     * @throws RemoteException
     */
    int uploadChunk(String realName, byte[] fileContent, int chunk, int totalChunk) throws RemoteException;

    /**
     * delete file in server.
     * @param realName real name of file
     * @return 0 is success, others if fail
     * @throws RemoteException
     */
    int unlink(String realName) throws RemoteException;

    /**
     * download chunks from server
     * @param randomName random number of file
     * @param offset offset of file
     * @param size size of chunk to download
     * @return byte array
     * @throws RemoteException
     */
    byte[] downloadChunk(String randomName, int offset, int size) throws RemoteException;

    /**
     * check server and download files
     * @param realName real name of file
     * @param o open option
     * @param version version number
     * @return CheckResult class
     * @throws RemoteException
     */
    CheckResult checkServer(String realName, FileHandling.OpenOption o, int version) throws RemoteException;

}
