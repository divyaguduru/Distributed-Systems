import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * @author guangyu
 * @version 1.0
 * @since 3/24/16
 */
public interface RMI extends Remote, Serializable {

    /**
     * This method is used by non-master server to ask their role (front or middle)
     * @param vmId id of server
     * @return front + id, middle + id
     * @throws RemoteException
     */
    String newServerRole(int vmId) throws RemoteException;

    /**
     * This method is used by front-end to forward request to server, this method is used to add server
     * @param request request to be send from server to master
     * @throws RemoteException
     */
    void forward(Cloud.FrontEndOps.Request request) throws RemoteException;

    /**
     * This method is used by middle server to get request from master to process.
     * @return request from server
     * @throws RemoteException
     */
    Cloud.FrontEndOps.Request getRequest() throws RemoteException;

    /**
     * this method is executed only in master and called in CacheDatabase Object
     * @param key key of request
     * @param value value of request
     * @throws RemoteException
     */
    void updateCache(String key, String value) throws RemoteException;

    /**
     * get cache result
     * @param key key
     * @return value result
     * @throws RemoteException
     */
    String getCache(String key) throws RemoteException;

    /**
     * master call front to shut down itself
     * @throws RemoteException
     */
    void shutDownFront() throws RemoteException;

    /**
     * master call middle to shut down itself
     * @throws RemoteException
     */
    void shutDownMiddle() throws RemoteException;
}
