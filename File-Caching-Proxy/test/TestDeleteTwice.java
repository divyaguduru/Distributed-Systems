import org.junit.Test;

/**
 * Created by guangyu on 3/4/16.
 */
public class TestDeleteTwice {
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

        int ret1 = handler.unlink("toDelete");
        System.err.println(ret1);
        int ret2 = handler.unlink("toDelete");
        System.err.println(ret2);
    }
}
