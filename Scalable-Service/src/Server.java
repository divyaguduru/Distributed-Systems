import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Server class, master server, front server, middle server all shares this class. There are mainly three roles for this
 * server, doMaster(), doFront(), doMiddle()
 */
public class Server implements RMI {
    /*
     * prefix for RMI method name
     */
    private static final String SERVER_RMI_URL = "master";
    private static final String VM_RMI_URL_PREFIX = "vm";
    private static final int MAX_MIDDLE = 8;

    /*
     * Server Lib
     */
    private ServerLib SL;
    private Registry registry;
    private boolean isMaster, isMasterMix;

    /*
     * front and middle info
     */
    private List<String> frontEnds, middles;
    private Queue<Cloud.FrontEndOps.Request> hub;
    private Map<Integer, String> newVMRoleMap;
    private int estimatedFront, estimatedMiddle;
    private int workingFront, workingMiddle;
    private RMI master, stub;
    private Map<String, String> cache;
    private List<Long> outSpeed;

    // start times stamp
    private long startTime;
    // id of this machine
    private int vmId;

    /**
     * construtor of server
     *
     * @param ip   ip of vm to bind
     * @param port port of vm to bind
     * @param vmId optional, id of vm
     * @throws RemoteException
     */
    public Server(String ip, int port, int vmId) throws RemoteException {
        SL = new ServerLib(ip, port);
        this.vmId = vmId;
        isMaster = true;
        isMasterMix = true;
        hub = new LinkedBlockingDeque<>();
        frontEnds = new CopyOnWriteArrayList<>();
        middles = new CopyOnWriteArrayList<>();
        startTime = new Date().getTime();
        newVMRoleMap = new ConcurrentHashMap<>();
        cache = new ConcurrentHashMap<>();
        outSpeed = new CopyOnWriteArrayList<>();
    }

    /**
     * main method
     *
     * @param args input args
     * @throws IllegalArgumentException
     * @throws RemoteException
     */
    public static void main(String[] args) throws IllegalArgumentException, RemoteException {
        if (args.length < 2) {
            throw new IllegalArgumentException("Need >= 2 args: <cloud_ip> <cloud_port> [<vm_id>]");
        }

        String ip = args[0];
        int port = Integer.parseInt(args[1]);
        int vmId = args.length == 3 ? Integer.parseInt(args[2]) : -1;

        Server server = new Server(ip, port, vmId);

        server.stub = (RMI) UnicastRemoteObject.exportObject(server, 0);
        server.registry = LocateRegistry.getRegistry(ip, port);
        try {
            server.registry.bind(SERVER_RMI_URL, server.stub);
        } catch (AlreadyBoundException e) {
            server.isMaster = false;
        }

        if (!server.isMaster) {
            try {
                server.registry.bind(VM_RMI_URL_PREFIX + vmId, server.stub);
            } catch (RemoteException | AlreadyBoundException e) {
                e.printStackTrace();
            }
        }

        /*
         * master is also front end, it will register itself to LB automatically. As to other front end, its
         * creator(master) will call frontRegister() to register. Middle server will not be registered.
         */
        if (server.isMaster) {
            server.SL.register_frontend();
            server.addMiddle();
            server.addMiddle();
            server.doMaster();
        } else {
            try {
                server.master = (RMI) server.registry.lookup(SERVER_RMI_URL);
            } catch (NotBoundException | RemoteException e) {
                e.printStackTrace();
            }
            server.setRole();
        }
    }

    /**
     * infinite loop for master server
     */
    private void doMaster() {
        int lastTimeStamp = -1;
        boolean onceDetected = false;
        workingMiddle = 1;
        workingFront = 1;
        estimatedMiddle = 1;
        estimatedFront = 1;
        List<Integer> trend = new ArrayList<>();

        CachedDatabase database = null;
        try {
            database = new CachedDatabase(SL.getDB(), this);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        while (true) {
            int currentTime = timeStamp();
            /*
             * for the first five second, master has to be both front and middle job
             */
            if (middles.size() == 0) {
                if (hub.size() != 0) {
                    Cloud.FrontEndOps.Request r = hub.poll();

                    final CachedDatabase finalDatabase = database;
                    final Cloud.FrontEndOps.Request finalR = r;
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            SL.processRequest(finalR, finalDatabase);
                        }
                    }).start();
                }
                if (currentTime == 2 && !onceDetected) {
                    onceDetected = true;

                    int toAddMiddle = (SL.getQueueLength() - 7) / 2;
                    toAddMiddle = toAddMiddle > 0 ? toAddMiddle : 0;
                    for (int i = 0; i < toAddMiddle; i++) {
                        System.out.println("s2 middle add");
                        addMiddle();
                    }

                    int toAddFront = (SL.getQueueLength() - 4) / 10;
                    toAddFront = toAddFront > 0 ? toAddFront : 0;
                    for (int i = 0; i < toAddFront; i++) {
                        System.out.println("s2 front add");
                        addFront();
                    }
                }
                /*
                 * at 5s, when the first middle is started
                 */
            } else if (isMasterMix) {
                isMasterMix = false;
                int currentLength = SL.getQueueLength();
                int maxRemain = 2;
                int toDropAmount = currentLength < maxRemain ? 0 : currentLength - maxRemain;
                System.out.println("hub to drop head " + toDropAmount + " time: " + timeStamp());

                for (int i = 0; i < toDropAmount; i++) {
                    SL.dropHead();
                }
                System.out.println("transit drop finished at " + timeStamp());
                workingMiddle -= 1;
            }

            /*
             * master front-end responsibility, will forward request to the queue in master(itself) node
             */
            if (SL.getQueueLength() != 0) {
                Cloud.FrontEndOps.Request r = SL.getNextRequest();
                try {
                    forward(r);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            int estimatedHubLength = getEstimatedHubLength();
            int recentSpeed = getRecentSpeed();

            /*
             * update time stamp
             */
            if (currentTime != lastTimeStamp) {
                System.out.println(" || time " + currentTime + " speed " + getRecentSpeed()
                        + " front " + workingFront + " middle " + workingMiddle + " || ");
                lastTimeStamp = currentTime;
                trend.add(recentSpeed);
            }

            /*
             * scale up according to trend
             */
            if (currentTime > 18) {
                int previousSpeed = trend.get(trend.size() - 2);
                if (recentSpeed - previousSpeed > 9) {
                    addFront();
                    addMiddle();
                }
            }

            /*
             * front-end scale out
             */
            if (SL.getQueueLength() > 12 * estimatedFront) {
                addFront();
            }

            /*
             * middle scale out
             */
            int toAddMiddle = (int) Math.ceil(Math.max((estimatedHubLength - estimatedMiddle * 2) / 1.8,
                    (hub.size() - estimatedMiddle * 4) / 1.8));
            toAddMiddle = (int) Math.ceil(Math.max(toAddMiddle, (recentSpeed - estimatedMiddle * 10) / 7.0));
            toAddMiddle = estimatedMiddle == MAX_MIDDLE || toAddMiddle < 0 ? 0 : toAddMiddle;
            for (int i = 0; i < toAddMiddle; i++) {
                System.out.println("scale out middle at " + timeStamp());
                addMiddle();
            }

            /*
             * scale in (front / middle)
             */
            if (currentTime >= 12) {
                if (MAX_MIDDLE == 8 && recentSpeed < (workingMiddle - 2) * 7) {
                    endMiddle();
                }

                if (recentSpeed < workingFront * 12) {
                    endFront();
                }
            }

            /*
             * drop request when it cannot be processed
             */
            int toDrop = Math.max(estimatedHubLength - workingMiddle * 2, hub.size() - workingMiddle * 3);
            for (int i = 0; i < toDrop; i++) {
                SL.drop(hub.poll());
                System.out.println(" hub drop ");
            }
        }
    }

    /**
     * infinite loop for front-end, the only job for front-end is to forward request to master server
     */
    private void doFront() {
        SL.register_frontend();
        System.out.println("do front job");
        while (true) {
            if (SL.getQueueLength() != 0) {
                Cloud.FrontEndOps.Request r = SL.getNextRequest();
                try {
                    master.forward(r);
                } catch (RemoteException ignored) {
                }
            }
        }
    }

    /**
     * infinite loop for middle server, the only job middle is to ask master for request to process
     */
    private void doMiddle() {
        System.out.println("do middle job");
        CachedDatabase database = null;
        try {
            database = new CachedDatabase(SL.getDB(), master);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        while (true) {
            try {
                Cloud.FrontEndOps.Request request = master.getRequest();
                if (request != null) {
                    SL.processRequest(request, database);
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * helper method used by master server to add front-end
     */
    private void addFront() {
        if (estimatedFront < 2) {

            estimatedFront += 1;

            int id = SL.startVM();

            newVMRoleMap.put(id, "front" + id);
        }
    }

    /**
     * helper method used by master server to add middle
     */
    private void addMiddle() {
        if (estimatedMiddle < MAX_MIDDLE) {

            estimatedMiddle += 1;

            int id = SL.startVM();

            newVMRoleMap.put(id, "middle" + id);
        }
    }

    /**
     * helper method used by master server to end front-end
     */
    private void endFront() {
        if (!frontEnds.isEmpty()) {
            String toShutDown = frontEnds.remove(0);
            int toShutDownId = Integer.valueOf(toShutDown.substring(5));
            workingFront--;
            estimatedFront--;
            System.err.println("shutting down front " + toShutDownId + " time " + timeStamp());
            try {
                RMI vm = (RMI) registry.lookup(VM_RMI_URL_PREFIX + toShutDownId);
                vm.shutDownFront();
                SL.endVM(toShutDownId);
            } catch (RemoteException | NotBoundException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * helper method used by master server to end middle
     */
    private void endMiddle() {
        if (!middles.isEmpty()) {
            String toShutDown = middles.remove(0);
            int toShutDownId = Integer.valueOf(toShutDown.substring(6));
            System.err.println("shutting down middle " + toShutDownId + " time " + timeStamp());
            workingMiddle--;
            estimatedMiddle--;
            try {
                RMI vm = (RMI) registry.lookup(VM_RMI_URL_PREFIX + toShutDownId);
                vm.shutDownMiddle();
                SL.endVM(toShutDownId);
            } catch (RemoteException | NotBoundException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * front-end, middle set roles
     */
    private void setRole() {
        String role = null;
        try {
            role = master.newServerRole(vmId);
            if (role.startsWith("front")) {
                // front end
                registry.bind(role, stub);
                doFront();
            } else {
                // back end
                registry.bind(role, stub);
                doMiddle();
            }
        } catch (RemoteException | AlreadyBoundException e) {
            System.out.println(role);
            e.printStackTrace();
        }
    }

    /**
     * get time stamp (in seconds) since master server starts
     * @return
     */
    private int timeStamp() {
        return (int) (new Date().getTime() - startTime) / 1000;
    }

    /**
     * get estimated queue length. There are two types of request. One is read request which is already cached, others
     * are the jobs done by local database which are time-consuming.
     * @return queue length
     */
    private int getEstimatedHubLength() {
        int length = 0;
        for (Cloud.FrontEndOps.Request r : hub) {
            if (r.isPurchase || !cache.containsKey(r.item)) {
                length++;
            }
        }
        return length;
    }

    /**
     * get incoming request speed in recent 3 seconds.
     * @return speed
     */
    private int getRecentSpeed() {
        int counter = 0;
        long currentTime = new Date().getTime();
        for (Long l : outSpeed) {
            if (currentTime - l < 1000 * 3) {
                counter++;
            }
        }
        return counter;
    }

    @Override
    public Cloud.FrontEndOps.Request getRequest() throws RemoteException {
        while (hub.isEmpty()) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return hub.poll();
    }

    @Override
    public void updateCache(String key, String value) throws RemoteException {
        cache.put(key, value);
    }

    @Override
    public String getCache(String key) throws RemoteException {
        return cache.get(key);
    }

    @Override
    public void shutDownFront() throws RemoteException {
        try {
            UnicastRemoteObject.unexportObject(this, true);
            SL.unregister_frontend();
            SL.shutDown();
        } catch (NoSuchObjectException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void shutDownMiddle() throws RemoteException {
        try {
            UnicastRemoteObject.unexportObject(this, true);
            SL.shutDown();
        } catch (NoSuchObjectException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String newServerRole(int vmId) throws RemoteException {
        String role = newVMRoleMap.get(vmId);
        if (role.startsWith("front")) {
            frontEnds.add(role);
            System.out.println(vmId + " front started " + timeStamp());
            workingFront += 1;
        } else {
            middles.add(role);
            System.out.println(vmId + " middle started " + timeStamp());
            workingMiddle += 1;
        }
        return role;
    }

    @Override
    public void forward(Cloud.FrontEndOps.Request request) throws RemoteException {
        if (request != null) {
            hub.add(request);
            outSpeed.add(new Date().getTime());
        }
    }
}
