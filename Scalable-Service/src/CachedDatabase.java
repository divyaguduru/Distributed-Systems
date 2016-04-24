import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Cached Database, this class implement original database and used as a cache tier. For read option, it will first
 * look up remote cache repo. For other operation, it will pass to local database directly.
 * @author guangyu
 * @version 1.0
 * @since 3/29/16
 */
public class CachedDatabase implements Cloud.DatabaseOps, Remote {
    private Cloud.DatabaseOps database;
    private RMI master;

    /**
     * Constructor of database.
     * @param database load database copy
     * @param master
     * @throws RemoteException
     */
    public CachedDatabase(Cloud.DatabaseOps database, RMI master) throws RemoteException {
        this.database = database;
        this.master = master;
    }

    @Override
    public String get(String s) throws RemoteException {
        String result = getRemote(s);
        if (result == null) {
            result = database.get(s);
            updateRemote(s, result);
        }
        return result;
    }

    @Override
    public boolean set(String s, String s1, String s2) throws RemoteException {
        return database.set(s, s1, s2);
    }

    @Override
    public boolean transaction(String s, float v, int i) throws RemoteException {
        return database.transaction(s, v, i);
    }

    /**
     * helper function to get key-value from remote repo. If no record is found, null will be returned.
     * @param key key to search in cache
     * @return value of key
     * @throws RemoteException
     */
    private String getRemote(String key) throws RemoteException {
        return master.getCache(key);
    }

    /**
     * helper function to update remote key value pair.
     * @param key key to update
     * @param value value to update
     * @throws RemoteException
     */
    private void updateRemote(String key, String value) throws RemoteException {
        master.updateCache(key, value);
    }
}
