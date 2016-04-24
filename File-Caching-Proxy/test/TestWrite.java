import org.junit.Test;

/**
 * Created by guangyu on 3/4/16.
 */
public class TestWrite {
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

        int fd = handler.open("testWrite", FileHandling.OpenOption.CREATE_NEW);
        System.out.println(fd);

        String testContent = "test modified upload";
        byte[] bytes = testContent.getBytes();
        long writeRet = handler.write(fd, bytes);
        System.out.println(writeRet);

        int closeRet = handler.close(fd);
        System.out.println(closeRet);
    }
}