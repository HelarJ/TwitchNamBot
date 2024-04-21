package chatbot.message;

import chatbot.singleton.Config;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public class LoggableMessage implements Message {

    private final String sender;
    private final String message;
    private final String uid;
    private final boolean subscribed;
    private final boolean whisper;
    private final String fullMsg;

    private final long twitchTimeMillis;

    public LoggableMessage(String sender, String uid, String message, boolean subscribed,
            boolean whisper, String fullMsg, String twitchTimestamp)
    {
        this.sender = sender;
        this.message = message;
        this.uid = uid;
        this.subscribed = subscribed;
        this.whisper = whisper;
        this.fullMsg = fullMsg;
        this.twitchTimeMillis = Long.parseLong(twitchTimestamp);

    }

    public Instant getInstant() {
        return Instant.ofEpochMilli(twitchTimeMillis);
    }

    public LocalDateTime getLocalDateTime() {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(twitchTimeMillis), ZoneOffset.UTC);
    }

    public Timestamp getTimestamp() {
        return Timestamp.valueOf(getLocalDateTime());
    }

    public String getFullMsg() {
        return fullMsg;
    }

    @Override
    public String getSender() {
        return sender;
    }

    @Override
    public String getStringMessage() {
        return message;
    }

    public String getUid() {
        return uid;
    }

    public boolean isSubscribed() {
        return subscribed;
    }

    public String getTime() {
        String timeStr = getInstant().toString().replaceAll("T", " ");
        if (timeStr.indexOf('.') == -1) {
            return timeStr.replaceAll("Z", "");
        } else {
            return timeStr.substring(0, timeStr.indexOf('.'));
        }
    }

    public String getUUID() {
        return UUID.nameUUIDFromBytes(uuidString().getBytes(StandardCharsets.UTF_8)).toString();
    }

    public boolean isWhisper() {
        return whisper;
    }

    public String uuidString() {
        if (sender.equalsIgnoreCase(Config.getInstance().getTwitchUsername())) {
            return sender + message + getTimestamp() + fullMsg;
        }

        return fullMsg;
    }

    @Override
    public String toString() {
        return sender + ": " + message;
    }

}
