package chatbot.message;

public class SimpleMessage implements Message {

    private final String sender;
    private final String message;

    public SimpleMessage(String sender, String message) {
        this.sender = sender;
        this.message = message;
    }

    @Override
    public String getSender() {
        return sender;
    }

    @Override
    public String getStringMessage() {
        return message;
    }
}
