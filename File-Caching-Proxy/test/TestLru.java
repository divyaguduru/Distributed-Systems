import org.junit.Test;

/**
 * Created by guangyu on 3/3/16.
 */
public class TestLru {
    @Test
    public void test() {
        String[] args = new String[4];
        args[0] = "127.0.0.1";
        args[1] = "11122";
        args[2] = "ProxyFile";
        args[3] = "17000000";

        Proxy proxy = new Proxy(args);

        // *** get test file ***
        Proxy.FileHandler handler = new Proxy.FileHandler(proxy);

        int fd1 = handler.open("file10M", FileHandling.OpenOption.READ);
        System.out.println("fd " + fd1);
        handler.close(fd1);
        proxy.printLru();

        int fd2 = handler.open("file5M", FileHandling.OpenOption.READ);
        System.out.println("fd " + fd2);
        handler.close(fd2);
        proxy.printLru();

        int fd3 = handler.open("file3M", FileHandling.OpenOption.READ);
        System.out.println("fd " + fd3);
        handler.close(fd3);
        proxy.printLru();

        int fd4 = handler.open("file10M", FileHandling.OpenOption.READ);
        System.out.println("fd " + fd4);
        handler.close(fd4);
        proxy.printLru();

        int fd5 = handler.open("file10M", FileHandling.OpenOption.READ);
        System.out.println("fd " + fd5);
        handler.close(fd5);
        proxy.printLru();
    }
}
