package chatbot.message;

import chatbot.enums.Command;
import chatbot.enums.Response;
import chatbot.utils.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CommandMessage implements Message {

    private final static Logger log = LogManager.getLogger(CommandMessage.class);

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
     * Parses the message and if it contains a command starting with a ! followed by a command
     * specified in the Command enum returns it.
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
            if (!cmdName.isEmpty()) {
                try {
                    return Command.valueOf(cmdName.toUpperCase());
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

    public CommandMessage setResponse(Response response) {
        this.response = response.toString();
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
        String messageWithoutCommand = getMessageWithoutCommand();
        String username = Utils.getArg(messageWithoutCommand, 0);
        if (username == null) {
            return messageWithoutCommand;
        }

        return messageWithoutCommand.substring(messageWithoutCommand.indexOf(username) + username.length()).trim();
    }

    public String getMessageWithoutCommand() {
        String command = Utils.getArg(message, 0);
        if (command == null || !message.startsWith("!")) {
            log.error("CommandMessage has no command: {}", message);
            return message;
        }
        return message.replaceFirst(command, "").strip();
    }

    @Override
    public String toString() {
        return sender + ": " + message;
    }

    public String getYear() {
        return Utils.getYear(Utils.getArg(getMessageWithoutUsername(), 0));
    }
}
