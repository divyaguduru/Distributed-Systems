import org.junit.Test;

/**
 * Created by guangyu on 3/4/16.
 */
public class TestLRU2 {
    @Test
    public void test() {
        String[] args = new String[4];
        args[0] = "127.0.0.1";
        args[1] = "11122";
        args[2] = "ProxyFile";
        args[3] = "5500000";

        Proxy proxy = new Proxy(args);

        // *** get test file ***
        Proxy.FileHandler handler = new Proxy.FileHandler(proxy);

        int fd = handler.open("A", FileHandling.OpenOption.READ);
        System.out.println("fd " + fd + "and keep opening");

        int fd11 = handler.open("./././A", FileHandling.OpenOption.READ);
        handler.close(fd11);

        int fd12 = handler.open("./././B", FileHandling.OpenOption.READ);
        handler.close(fd12);

        int fd13 = handler.open("./././C", FileHandling.OpenOption.READ);
        handler.close(fd13);

        int fd21 = handler.open("./././D", FileHandling.OpenOption.READ);
        handler.close(fd21);

        int fd22 = handler.open("./././E", FileHandling.OpenOption.READ);
        handler.close(fd22);

        int fd23 = handler.open("./././F", FileHandling.OpenOption.WRITE);
//        handler.close(fd23);

        int fd24 = handler.open("./././G", FileHandling.OpenOption.WRITE);
//        handler.close(fd24);
    }
}
