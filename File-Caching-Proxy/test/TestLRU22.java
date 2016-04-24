import org.junit.Test;

/**
 * Created by guangyu on 3/4/16.
 */
public class TestLRU22 {
    @Test
    public void test() {
        String[] args = new String[4];
        args[0] = "127.0.0.1";
        args[1] = "11122";
        args[2] = "ProxyFile";
        args[3] = "5500000";

        Proxy proxy = new Proxy(args);

        // *** get test file ***

        for(int i = 0; i < 3 ;i ++) {
            new Runnable() {
                @Override
                public void run() {
                    Proxy.FileHandler handler = new Proxy.FileHandler(proxy);
                    handler.open("hust", FileHandling.OpenOption.READ);
                }
            }.run();
        }

    }
}
