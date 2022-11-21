package chatbot.service;

import chatbot.ConsoleMain;
import chatbot.connector.MessageConnector;
import chatbot.message.CommandMessage;
import chatbot.message.LoggableMessage;
import chatbot.message.SimpleMessage;
import chatbot.message.TimeoutMessage;
import chatbot.singleton.ConfigSingleton;
import chatbot.singleton.SharedStateSingleton;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class ListenerService extends AbstractExecutionThreadService {

  private final SharedStateSingleton state = SharedStateSingleton.getInstance();
  private final ConfigSingleton config = ConfigSingleton.getInstance();
  private final MessageConnector messageConnector;

  public ListenerService(MessageConnector messageConnector) {
    this.messageConnector = messageConnector;
  }

  @Override
  protected void shutDown() {
    log.debug("{} stopped.", ListenerService.class);
  }

  @Override
  protected void startUp() {
    log.debug("{} started.", ListenerService.class);
  }

  @Override
  public void run() {
    while (state.isBotStillRunning()) {
      String output = getOutput();
      if (output == null) {
        break;
      }

      int index = 0;
      String source = "";
      String command;
      String params = "";
      Map<String, String> tagsMap = new HashMap<>();

      if (output.startsWith("@")) {
        int tagsEndIndex = output.indexOf(' ');
        String tags = output.substring(1, tagsEndIndex);
        tagsMap = tagsToMap(tags);
        index = tagsEndIndex + 1;
      }

      if (output.charAt(index) == ':') {
        index += 1;
        int sourceEndIndex = output.indexOf(" ", index);
        source = output.substring(index, sourceEndIndex);
        index = sourceEndIndex + 1;
      }

      int paramsEndIndex = output.indexOf(':', index);
      if (paramsEndIndex == -1) {
        paramsEndIndex = output.length();
      }

      command = output.substring(index, paramsEndIndex).trim();

      if (paramsEndIndex != output.length()) {
        index = paramsEndIndex + 1;
        params = output.substring(index);
      }

      String formattedOutput = """
          original: %s
          tags: %s
          source: %s
          command: %s
          params: %s
          """.formatted(output, tagsMap.toString(), source, command, params);

      log.trace(formattedOutput);

      String[] commandParts = command.split(" ");
      if (commandParts.length == 0) {
        log.error("Unexpected message %s".formatted(output));
        continue;
      }

      switch (commandParts[0]) {
        case "PING" -> handlePing();
        case "PRIVMSG" -> handleRegularMessage(output, source, tagsMap, params);
        case "WHISPER" -> handleWhisper(output, params);
        case "CLEARCHAT" -> handleTimeout(output, tagsMap, params);
        case "USERSTATE" -> log.info("Message sent successfully.");
        case "001", "002", "003", "004", "353", "366", "372", "375", "376", "CAP" ->
            log.info(output);
        case "USERNOTICE", "CLEARMSG", "PART", "JOIN" -> handleIgnored(output);
        case "RECONNECT" -> {
          log.info("Reconnect issued.");
          reconnect();
          return;
        }
        default -> log.info("Unhandled: %s".formatted(formattedOutput));
      }
    }
  }

  private Map<String, String> tagsToMap(String tags) {
    Map<String, String> tagsMap = new HashMap<>();
    String[] tagsSplit = tags.split(";");
    for (String tag : tagsSplit) {
      int index = tag.indexOf("=");
      String key = tag.substring(0, index);
      String value = tag.substring(index + 1);
      tagsMap.put(key, value);
    }
    return tagsMap;
  }

  private void reconnect() {
    if (state.isBotStillRunning()) {
      ConsoleMain.reconnect();
    }
  }

  private void handleIgnored(String output) {
    log.trace(output);
  }

  private void handleTimeout(String output, Map<String, String> tagsMap, String name) {
    if (tagsMap.get("target-user-id") == null) {
      log.info("Chat was cleared");
      return;
    }

    String userid = tagsMap.get("user-id");

    int banTime = Integer.parseInt(tagsMap.get("ban-duration"));
    log.debug("{} timed out for {}s", name, banTime);

    if (banTime >= 121059319) {
      state.commandHandlerBlockingQueue.add(
          new CommandMessage("Autoban", "!adddisabled " + name));
      state.messageLogBlockingQueue.add(
          new LoggableMessage(name, userid, "User was permanently banned.", false, false,
              output));
      state.increasePermabanCount();
    }
    state.timeoutBlockingQueue.add(new TimeoutMessage(name, userid, banTime));
  }

  private void handleRegularMessage(String fullMessage, String source, Map<String, String> tagsMap,
      String message) {
    String name = source.substring(0, source.indexOf("!"));

    String userid = tagsMap.get("user-id");

    String subscriberStr = tagsMap.get("subscriber");
    boolean subscribed = subscriberStr.equals("1");

    String outputMSG = message;
    if (outputMSG.startsWith("\u0001ACTION ")) {
      outputMSG = outputMSG.replaceAll("\u0001", "");
      outputMSG = outputMSG.replaceFirst("ACTION", "/me");
    }
    state.messageLogBlockingQueue.add(
        new LoggableMessage(name, userid, outputMSG, subscribed, false, fullMessage));
    state.commandHandlerBlockingQueue.add(new CommandMessage(name, outputMSG));

    //records a timeout with a 0-second duration to prevent timeoutlist exploting.
    state.timeoutBlockingQueue.add(new TimeoutMessage(name, userid, 0));

  }

  private void handleWhisper(String output, String message) {
    String name = output.substring(output.indexOf("display-name="));
    name = name.substring(13, name.indexOf(";"));

    log.info("User {} whispered {}.", name, message);

    state.messageLogBlockingQueue.add(
        new LoggableMessage(name, "NULL", message, false, true, output));

    if (name.equalsIgnoreCase(config.getBotAdmin())) {
      String[] commandSplit = message.split(" ");
      if (commandSplit.length == 0) {
        return;
      }

      switch (commandSplit[0]) {
        case "/shutdown" -> state.stop();
        case "/send" -> {
          message = message.substring("/send".length()).trim();
          state.sendingBlockingQueue.add(new SimpleMessage(name, message));
        }
        case "/restart" -> reconnect();
      }
    }
  }


  private String getOutput() {
    try {
      return messageConnector.getMessage();
    } catch (IOException e) {
      log.warn("Connection error for Listener: {}", e.getMessage());
      return null;
    }
  }

  private void handlePing() {
    try {
      messageConnector.sendMessage("PONG :tmi.twitch.tv\r\n");
    } catch (IOException e) {
      log.error("Error sending ping: {}", e.getMessage());
    }
  }
}
