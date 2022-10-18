package ChatBot.dataclass;

import ChatBot.enums.Command;
import ChatBot.enums.MessageType;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.Locale;

@Nonnull
public class Message {
    private String sender;
    private String message;
    private String uid;
    private boolean subscribed;
    private Instant time;
    private boolean whisper;
    private String fullMsg;
    public final MessageType type;

    public Message(String sender, String uid, String message, boolean subscribed, boolean whisper, String fullMsg) {
        this.sender = sender;
        this.message = message;
        this.uid = uid;
        this.subscribed = subscribed;
        this.time = Instant.now();
        this.whisper = whisper;
        this.fullMsg = fullMsg;
        this.type = MessageType.MESSAGE;
    }

    public Message(MessageType type) {
        this.type = type;
    }

    public Message(String message) {
        this.message = message;
        this.type = MessageType.MESSAGE;
    }


    public String getFullMsg() {
        return fullMsg;
    }

    public boolean isPoison() {
        return this.type == MessageType.POISON;
    }

    public String getSender() {
        return sender;
    }

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
        String timeStr = time.toString().replaceAll("T", " ");
        if (timeStr.indexOf('.') == -1) {
            return timeStr;
        } else {
            return timeStr.substring(0, timeStr.indexOf('.'));
        }
    }

    public boolean isWhisper() {
        return whisper;
    }

    public Command getCommand() {
        if (message.startsWith("!")) {
            String cmdName = message.toLowerCase().replaceAll("\uDB40\uDC00", "").stripTrailing().split(" ")[0].substring(1);
            if (cmdName.length() > 0) {
                try {
                    return Command.valueOf(cmdName.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return null;
    }

    public String getArguments() {
        String[] split = message.replaceAll("\uDB40\uDC00", "").stripTrailing().split(" ", 2);
        if (split.length == 2) {
            return split[1];
        } else {
            return "";
        }
    }

    @Override
    public String toString() {
        return sender + ": " + message;
    }
}
