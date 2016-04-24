import java.io.RandomAccessFile;

/**
 * Contains of file descriptor information, such as file name, its copy name and version number and permission
 */
public class FdDetail {
    private RandomAccessFile raFile;
    private String realName;
    private String randomName;
    private int originalVersion;
    private int permission; // 4 for read, 6 for read and write

    /**
     * constructor
     * @param raFile random access file
     * @param realName real name
     * @param randomName random name
     * @param originalVersion version
     * @param permission permission
     */
    public FdDetail(RandomAccessFile raFile, String realName, String randomName, int originalVersion, int permission) {
        this.raFile = raFile;
        this.realName = realName;
        this.randomName = randomName;
        this.originalVersion = originalVersion;
        this.permission = permission;
    }

    public RandomAccessFile getRaFile() {
        return raFile;
    }

    public String getRealName() {
        return realName;
    }

    public String getRandomName() {
        return randomName;
    }

    public int getOriginalVersion() {
        return originalVersion;
    }

    public int getPermission() {
        return permission;
    }

    /**
     * convert fd info into string
     * @return string
     */
    @Override
    public String toString() {
        return this.raFile + " " + this.realName + " " + this.randomName + " " + this.originalVersion +" " + this.permission;
    }
}
