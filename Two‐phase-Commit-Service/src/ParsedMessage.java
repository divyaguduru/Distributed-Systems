import java.util.Arrays;

/**
 * Parsed message class. ProjectLib.Message is in byte form and hard to read. This class is constructed from a
 * ProjectLib.Message and will facilitate reading.
 *
 * @author guangyu
 * @version 1.0
 * @since 4/13/16
 */
class ParsedMessage {
    public final int code;
    final String compositeFileName;
    String nodeId = null;
    boolean voteResult = false;
    byte[] img;
    String[] imageNames = null;

    /**
     * Constructor
     *
     * @param message ProjectLib.Message
     */
    ParsedMessage(ProjectLib.Message message) {
        byte[] body = message.body;
        int loc = getSeparateLac(body);

        byte[] bodyHeaderByte = Arrays.copyOfRange(body, 0, loc);
        img = Arrays.copyOfRange(body, loc + 1, body.length);

        String bodyHeader = new String(bodyHeaderByte);
        String[] tmp = bodyHeader.split("!");
        code = Integer.parseInt(tmp[0]);
        tmp = tmp[1].split("@");
        compositeFileName = tmp[0];

        if (code == MessageBuilder.VOTE) {
            imageNames = tmp[1].split("#");
        } else if (code == MessageBuilder.VOTE_RESPONSE) {
            tmp = tmp[1].split("#");
            nodeId = tmp[0];
            voteResult = tmp[1].equals("1");
        } else if (code == MessageBuilder.ACK) {
            nodeId = tmp[1];
        }
    }

    /**
     * helper method to get a separator from byte-form array.
     *
     * @param bytes message bytes body
     * @return location of separator
     */
    private int getSeparateLac(byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] == '\0') {
                return i;
            }
        }
        return -1;
    }
}
