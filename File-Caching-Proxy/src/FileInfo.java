import java.io.Serializable;

/**
 * Contains of information of files, such as real name, random name and version number.
 */
public class FileInfo implements Serializable {
    private String realName;
    private String randomName;
    private int version;

    /**
     * constructor
     * @param realName real name
     * @param randomName random name
     * @param version version number
     */
    public FileInfo(String realName, String randomName, int version) {
        this.realName = realName;
        this.randomName = randomName;
        this.version = version;
    }

    public String getRealName() {
        return realName;
    }

    public String getRandomName() {
        return randomName;
    }

    public int getVersion() {
        return version;
    }

    /**
     * update file version number
     * @return new version number
     */
    public int updateVersion() {
        version += 1;
        return version;
    }

    /**
     * update random name
     * @param randomName random name
     */
    public void updateRandomName(String randomName) {
        this.randomName = randomName;
    }

    /**
     * update random name and version
     * @param randomName random name
     * @param version version
     */
    public void updateCacheInfo(String randomName, int version) {
        this.randomName = randomName;
        this.version = version;
    }
}
