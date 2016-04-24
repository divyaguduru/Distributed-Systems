import org.junit.Test;

/**
 * Created by guangyu on 3/3/16.
 */
public class TestBigFile {
    @Test
    public void test() {
        String[] args = new String[4];
        args[0] = "127.0.0.1";
        args[1] = "11122";
        args[2] = "ProxyFile";
        args[3] = "10000000";

        Proxy proxy = new Proxy(args);

        // *** get test file ***
        Proxy.FileHandler handler = new Proxy.FileHandler(proxy);

        int fd = handler.open("file11M", FileHandling.OpenOption.READ);
        System.out.println("fd " + fd);
    }
}
