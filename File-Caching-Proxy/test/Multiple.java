import org.junit.Test;

/**
 * Created by guangyu on 3/3/16.
 */
public class Multiple {

    @Test
    public void test() {
        String[] args = new String[4];
        args[0] = "127.0.0.1";
        args[1] = "11122";
        args[2] = "ProxyFile";
        args[3] = "17000000";
        Proxy proxy = new Proxy(args);

        Runnable[] pool = new MulOpen[100];
        for (int i = 0; i < 100; i++) {
            pool[i] = new MulOpen(proxy);
        }
        for (int i = 0; i < 100; i++) {
            pool[i].run();
        }
    }

    private static class MulOpen implements Runnable {

        private Proxy proxy = null;

        MulOpen(Proxy proxy) {
            this.proxy = proxy;
        }

        @Override
        public void run() {
            Proxy.FileHandler handler = new Proxy.FileHandler(proxy);
            handler.open("file500K", FileHandling.OpenOption.READ);
        }
    }
}
