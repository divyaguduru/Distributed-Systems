/**
 * Created by guangyu on 2/20/16.
 */

import org.junit.Test;

import java.net.MalformedURLException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.Arrays;

public class TestRMI {
    @Test
    public void test() throws RemoteException, NotBoundException, MalformedURLException {

        String[] args = new String[4];
        args[0] = "127.0.0.1";
        args[1] = "11122";
        args[2] = "ProxyFile";
        args[3] = "10000";

        Proxy proxy = new Proxy(args);

        // *** create test file ***
        Proxy.FileHandler handler = new Proxy.FileHandler(proxy);
        int fd = handler.open("testUpload", FileHandling.OpenOption.CREATE);
        System.out.println("open return " + fd);

        String testContent = "test upload";
        byte[] bytes = testContent.getBytes();
        long writeRet = handler.write(fd, bytes);
        System.out.println("write return " + writeRet);

        int closeRet = handler.close(fd);
        System.out.println("close return " + closeRet);



        // *** read created file ***

        int secondFd = handler.open("testUpload", FileHandling.OpenOption.READ);
        System.out.println("second fd " + secondFd);

        int secoondcloseRet = handler.close(secondFd);
        System.out.println("second close return " + secoondcloseRet);

        // *** update created file ***
        int secondWriteFd = handler.open("testUpload", FileHandling.OpenOption.WRITE);
        System.out.println("second write fd" + secondWriteFd);

        testContent = "second write test upload";
        bytes = testContent.getBytes();
        writeRet = handler.write(secondWriteFd, bytes);
        System.out.println("second write return " + writeRet);

        closeRet = handler.close(secondWriteFd);
        System.out.println("second write close return " + closeRet);

        // *** open to test updated file ***
        int aftertwoVersion = handler.open("testUpload", FileHandling.OpenOption.READ);
        System.out.println("after two version " + aftertwoVersion);
    }
}
