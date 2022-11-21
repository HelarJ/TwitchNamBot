package chatbot.service;

import chatbot.ConsoleMain;
import chatbot.connector.MessageConnector;
import chatbot.connector.container.IncomingMessage;
import chatbot.message.CommandMessage;
import chatbot.message.LoggableMessage;
import chatbot.message.SimpleMessage;
import chatbot.message.TimeoutMessage;
import chatbot.singleton.ConfigSingleton;
import chatbot.singleton.SharedStateSingleton;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import java.io.IOException;
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
      IncomingMessage incomingMessage = new IncomingMessage(output);
      log.trace(incomingMessage);

      switch (incomingMessage.getCommand()) {
        case "PING" -> handlePing();
        case "PRIVMSG" -> handleRegularMessage(incomingMessage);
        case "WHISPER" -> handleWhisper(incomingMessage);
        case "CLEARCHAT" -> handleTimeout(incomingMessage);
        case "USERSTATE" -> log.info("Message sent successfully.");
        case "001", "002", "003", "004", "353", "366", "372", "375", "376", "CAP" ->
            log.info(incomingMessage.original);
        case "USERNOTICE", "CLEARMSG", "PART", "JOIN" -> handleIgnored(incomingMessage);
        case "RECONNECT" -> {
          log.info("Reconnect issued.");
          reconnect();
          return;
        }
        default -> log.info("Unhandled: %s".formatted(incomingMessage));
      }
    }
  }

  private void reconnect() {
    if (state.isBotStillRunning()) {
      ConsoleMain.reconnect();
    }
  }

  private void handleIgnored(IncomingMessage incomingMessage) {
    log.trace("Ignored: %s".formatted(incomingMessage));
  }

  private void handleTimeout(IncomingMessage incomingMessage) {
    if (incomingMessage.tagsMap.get("target-user-id") == null) {
      log.info("Chat was cleared");
      return;
    }
    String userid = incomingMessage.tagsMap.get("target-user-id");

    int banTime = Integer.parseInt(incomingMessage.tagsMap.get("ban-duration"));
    String name = incomingMessage.params;
    log.debug("{} timed out for {}s", name, banTime);

    if (banTime >= 121059319) {
      state.commandHandlerBlockingQueue.add(
          new CommandMessage("Autoban", "!adddisabled " + name));
      state.messageLogBlockingQueue.add(
          new LoggableMessage(name, userid, "User was permanently banned.", false, false,
              incomingMessage.original));
      state.increasePermabanCount();
    }
    state.timeoutBlockingQueue.add(new TimeoutMessage(name, userid, banTime));
  }

  private void handleRegularMessage(IncomingMessage incomingMessage) {

    String name = incomingMessage.getName();
    String userid = incomingMessage.tagsMap.get("user-id");
    boolean subscribed = incomingMessage.tagsMap.get("subscriber").equals("1");

    String outputMSG = incomingMessage.params;
    if (outputMSG.startsWith("\u0001ACTION ")) {
      outputMSG = outputMSG.replaceAll("\u0001", "");
      outputMSG = outputMSG.replaceFirst("ACTION", "/me");
    }
    state.messageLogBlockingQueue.add(
        new LoggableMessage(name, userid, outputMSG, subscribed, false, incomingMessage.original));
    state.commandHandlerBlockingQueue.add(new CommandMessage(name, outputMSG));

    //records a timeout with a 0-second duration to prevent timeoutlist exploting.
    state.timeoutBlockingQueue.add(new TimeoutMessage(name, userid, 0));

  }

  private void handleWhisper(IncomingMessage incomingMessage) {
    String name = incomingMessage.tagsMap.get("display-name");
    String message = incomingMessage.params;
    log.info("User {} whispered {}.", name, message);

    state.messageLogBlockingQueue.add(
        new LoggableMessage(name, "NULL", message, false, true, incomingMessage.original));

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
