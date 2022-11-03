package chatbot.service;

import chatbot.ConsoleMain;
import chatbot.connector.MessageConnector;
import chatbot.message.CommandMessage;
import chatbot.message.LoggableMessage;
import chatbot.message.Message;
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
        return;
      }
      if (output.equals("PING :tmi.twitch.tv")) {
        handlePing();
        continue;
      }
      if (output.contains(".tmi.twitch.tv PRIVMSG")) {
        handleRegularMessage(output);
      } else if (output.contains(".tmi.twitch.tv WHISPER")) {
        handleWhisper(output);
      } else if (output.contains("CLEARCHAT")) {
        if (!output.contains("target-user-id")) {
          log.info("Chat was cleared");
          continue;
        }
        handleTimeout(output);
      } else if (output.contains("USERNOTICE")) {
        handleSkipped(output);
      } else if (output.contains(".tv USERSTATE ")) {
        log.info("Message sent successfully.");
      } else if (output.contains(".tv CLEARMSG ")) {
        handleSkipped(output);
      } else if (output.contains(".tv PART ")) {
        handleSkipped(output);
      } else if (output.contains(".tv JOIN ")) {
        handleSkipped(output);
      } else {
        log.info(output);
      }
    }

    if (state.isBotStillRunning()) {
      ConsoleMain.reconnect();
    }
  }

  private void handleSkipped(String output) {
    log.trace(output);
  }

  private void handleTimeout(String output) {
    String name = output.substring(output.indexOf("#"));
    name = name.substring(name.indexOf(":") + 1);

    String userid = output.substring(output.indexOf("user-id="));
    userid = userid.substring(8, userid.indexOf(";"));

    int banTime = Integer.parseInt(
        output.substring(output.indexOf("=") + 1, output.indexOf(";")).strip());
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

  private void handleWhisper(String output) {
    String name = output.substring(output.indexOf("display-name="));
    name = name.substring(13, name.indexOf(";"));
    String msg = output.substring(output.indexOf(config.getTwitchUsername().toLowerCase()));
    msg = msg.substring(msg.indexOf(":") + 1);
    log.info("User {} whispered {}.", name, msg);
    Message message = new LoggableMessage(name, "NULL", msg, false, true, output);
    state.messageLogBlockingQueue.add(message);
    if (name.equalsIgnoreCase(config.getBotAdmin())) {
      if (msg.equals("/shutdown")) {
        state.stop();
      } else if (msg.startsWith("/send ")) {
        msg = msg.substring(6);
        state.sendingBlockingQueue.add(new SimpleMessage(name, msg));
      } else if (msg.startsWith("/banuser ")) {
        log.info("Banned user {}", msg.substring(9));
      } else if (msg.equals("/restart")) {
        ConsoleMain.reconnect();
      }
    }
  }

  private void handleRegularMessage(String output) {
    String name = output.substring(0, output.indexOf(".tmi.twitch.tv PRIVMSG "));
    name = name.substring(name.lastIndexOf("@") + 1);

    String userid = output.substring(output.indexOf(";user-id="));
    userid = userid.substring(9);
    userid = userid.substring(0, userid.indexOf(";"));

    String subscriberStr = output.substring(output.indexOf("subscriber="));
    subscriberStr = subscriberStr.substring(11, subscriberStr.indexOf(";"));
    boolean subscribed = subscriberStr.equals("1");

    String message = output.substring(output.indexOf("PRIVMSG"));
    //String channelName = message.substring(message.indexOf("#"), message.indexOf(":")-1);
    String outputMSG = message.substring(message.indexOf(":") + 1);
    if (outputMSG.startsWith("\u0001ACTION ")) {
      outputMSG = outputMSG.replaceAll("\u0001", "");
      outputMSG = outputMSG.replaceFirst("ACTION", "/me");
    }
    state.messageLogBlockingQueue.add(
        new LoggableMessage(name, userid, outputMSG, subscribed, false, output));
    state.commandHandlerBlockingQueue.add(new CommandMessage(name, outputMSG));

    //records a timeout with a 0-second duration to prevent timeoutlist exploting.
    state.timeoutBlockingQueue.add(new TimeoutMessage(name, userid, 0));

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
