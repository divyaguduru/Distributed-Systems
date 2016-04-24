import org.junit.Test;

/**
 * Created by guangyu on 3/4/16.
 */
public class TestCreateNew {
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

        int fd = handler.open("testCreateNew", FileHandling.OpenOption.CREATE_NEW);
        System.out.println(fd);

        String testContent = "test create new upload";
        byte[] bytes = testContent.getBytes();
        long writeRet = handler.write(fd, bytes);
        System.out.println(writeRet);

        int closeRet = handler.close(fd);
        System.out.println(closeRet);

        int fd2 = handler.open("testCreateNew", FileHandling.OpenOption.READ);

    }
}
