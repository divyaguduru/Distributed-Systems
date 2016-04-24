/**
 * MessageBuilder Class. This method contains several message code and a build method to facilitate build a message object
 * from a string message and an image byte array.
 *
 * @author guangyu
 * @version 1.0
 * @since 4/13/16
 */
class MessageBuilder {
    static final int VOTE = 1;
    static final int VOTE_RESPONSE = 2;
    static final int EXEC = 3;
    static final int ACK = 4;
    static final int ABORT = 5;

    /**
     * Method to build message from a string and an image byte array
     *
     * @param addr  receiver of message
     * @param text  text message
     * @param image imaget byte array
     * @return Message class
     */
    static ProjectLib.Message build(String addr, String text, byte[] image) {
        if (image == null) {
            image = new byte[0];
        }
        byte[] bodyBytes = text.getBytes();
        byte[] msgBody = new byte[bodyBytes.length + 1 + image.length];
        System.arraycopy(bodyBytes, 0, msgBody, 0, bodyBytes.length);
        msgBody[bodyBytes.length] = '\0';
        System.arraycopy(image, 0, msgBody, bodyBytes.length + 1, image.length);
        return new ProjectLib.Message(addr, msgBody);
    }
}
