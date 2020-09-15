package ChatBot;

import java.time.Instant;

public class Command {
    private final String sender;
    private final String message;
    private final String uid;
    private final boolean subscribed;
    private final Instant time;
    private final boolean whisper;
    private final String fullMsg;

    public Command (String sender, String uid, String message, boolean subscribed, boolean whisper, String fullMsg) {
        this.sender = sender;
        this.message = message;
        this.uid = uid;
        this.subscribed = subscribed;
        this.time = Instant.now();
        this.whisper = whisper;
        this.fullMsg = fullMsg;
    }

    public String getFullMsg() {
        return fullMsg;
    }

    public String getSender() {
        return sender;
    }

    public String getMessage() {
        return message;
    }

    public String getUid() {
        return uid;
    }

    public boolean isSubscribed() {
        return subscribed;
    }

    public String getTime(){
        String timeStr = time.toString().replaceAll("T", " ");
        return timeStr.substring(0,timeStr.indexOf('.'));
    }

    public boolean isWhisper() {
        return whisper;
    }
}
