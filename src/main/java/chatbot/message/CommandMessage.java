package chatbot.message;

import chatbot.enums.Command;
import chatbot.utils.Utils;
import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.Locale;

@Log4j2
public class CommandMessage implements Message {
    private final String sender;
    private final String message;
    private String response;

    public CommandMessage(String sender, String message) {
        this.sender = sender;
        this.message = message;
    }

    @Override
    public String getSender() {
        return sender;
    }

    @Override
    public String getStringMessage() {
        if (response != null) {
            return response;
        }
        return message;
    }

    /**
     * Parses the message and if it contains a command starting with a ! followed by a command specified in the Command enum returns it.
     *
     * @return Command or null if the message didn't contain a recognizable command.
     */
    public Command getCommand() {
        if (message.startsWith("!")) {
            String cmdName = message.toLowerCase()
                    .replaceAll("\uDB40\uDC00", "")
                    .stripTrailing()
                    .split(" ")[0]
                    .substring(1);
            if (cmdName.length() > 0) {
                try {
                    return Command.valueOf(cmdName.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return null;
    }

    public CommandMessage setResponse(String response) {
        this.response = response;
        return this;
    }

    public String getUsername() {
        String username = Utils.getArg(getMessageWithoutCommand().replaceAll("\uDB40\uDC00", ""), 0);
        if (username == null) {
            username = sender;
        }
        return Utils.cleanName(sender, username);
    }

    public String getMessageWithoutUsername() {
        String username = Utils.getArg(getMessageWithoutCommand(), 0);
        if (username == null) {
            return getMessageWithoutCommand();
        }
        return getMessageWithoutCommand().replaceFirst(username, "").strip();
    }

    public String getMessageWithoutCommand() {
        String command = Utils.getArg(message, 0);
        if (command == null || !message.startsWith("!")) {
            log.error("CommandMessage has no command: {}", message);
            return message;
        }
        return message.replaceFirst(command, "").strip();
    }

    public List<String> getWordList() {
        return Utils.getWordList(getMessageWithoutUsername());
    }

    @Override
    public String toString() {
        return sender + ": " + message;
    }

    public String getYear() {
        return Utils.getYear(Utils.getArg(getMessageWithoutUsername(), 0));
    }
}
