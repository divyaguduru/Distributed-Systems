import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Created by guangyu on 3/4/16.
 */
public class TestRelativePath {
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
        int fd = handler.open("fileroot/subdir/subdir/../subdir/../../subdir/./subdir/huge_file1457117060357", FileHandling.OpenOption.READ);
        System.out.println(fd);
    }
}
