/**
 * Created by guangyu on 3/3/16.
 */

import org.junit.Test;

import java.net.MalformedURLException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;

public class Test2 {
    @Test
    public void test() throws RemoteException, NotBoundException, MalformedURLException {
        String[] args = new String[4];
        args[0] = "127.0.0.1";
        args[1] = "11122";
        args[2] = "ProxyFile";
        args[3] = "10000000";

        Proxy proxy = new Proxy(args);

        // *** get test file ***
        Proxy.FileHandler handler = new Proxy.FileHandler(proxy);

        int fd = handler.open("file500K", FileHandling.OpenOption.READ);

        int anotherFileFD = handler.open("hust", FileHandling.OpenOption.READ);

        handler.close(fd);
        proxy.printLru();

        handler.close(anotherFileFD);
        proxy.printLru();

        // *** read second file ***
        int secondFd = handler.open("file500K", FileHandling.OpenOption.READ);

        handler.close(secondFd);
    }
}
