import org.junit.Test;

/**
 * Created by guangyu on 3/4/16.
 */
public class TestLRU1 {
    @Test
    public void test() throws InterruptedException {
        String[] args = new String[4];
        args[0] = "127.0.0.1";
        args[1] = "11122";
        args[2] = "ProxyFile";
        args[3] = "5500000";

        Proxy proxy = new Proxy(args);

        // *** get test file ***
        Proxy.FileHandler handler = new Proxy.FileHandler(proxy);

        int fd11 = handler.open("./././A", FileHandling.OpenOption.READ);
        handler.close(fd11);

        int fd12 = handler.open("./././B", FileHandling.OpenOption.READ);
        handler.close(fd12);

        int fd13 = handler.open("./././C", FileHandling.OpenOption.READ);
        handler.close(fd13);

        int fd21 = handler.open("./././B", FileHandling.OpenOption.READ);
        handler.close(fd21);

        int fd22 = handler.open("./././D", FileHandling.OpenOption.READ);
        handler.close(fd22);

        int fd23 = handler.open("./././E", FileHandling.OpenOption.READ);
        handler.close(fd23);

        int fd24 = handler.open("./././B", FileHandling.OpenOption.READ);
        handler.close(fd24);

        int fd31 = handler.open("./././F", FileHandling.OpenOption.READ);
        handler.close(fd31);

        int fd32 = handler.open("./././G", FileHandling.OpenOption.READ);
        handler.close(fd32);

        new Runnable() {

            @Override
            public void run() {
                String[] args = new String[4];
                args[0] = "127.0.0.1";
                args[1] = "11122";
                args[2] = "ProxyFile2";
                args[3] = "5500000";

                System.out.println("****_______******");
                Proxy proxy2 = new Proxy(args);

                // *** get test file ***
                Proxy.FileHandler handler = new Proxy.FileHandler(proxy2);

                int fd41 = handler.open("./A", FileHandling.OpenOption.WRITE);
                proxy2.printLru();
                handler.close(fd41);

                int fd42 = handler.open("./F", FileHandling.OpenOption.WRITE);
                proxy2.printLru();
                handler.close(fd42);
                System.out.println("****_______******");

            }
        }.run();

        Thread.sleep(5000);

        int fd51 = handler.open("./././F", FileHandling.OpenOption.READ);
        handler.close(fd51);

        int fd52 = handler.open("./././A", FileHandling.OpenOption.READ);
        handler.close(fd52);

        int fd53 = handler.open("./././C", FileHandling.OpenOption.READ);
        handler.close(fd53);
        proxy.printLru();
    }
}
