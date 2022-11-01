package chatbot;

import chatbot.connector.MessageConnector;
import chatbot.connector.TwitchMessageConnector;
import chatbot.singleton.SharedStateSingleton;
import java.io.IOException;
import java.time.Instant;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class ConsoleMain {

  private static final Instant startingTime = Instant.now();
  private static final SharedStateSingleton state = SharedStateSingleton.getInstance();
  private static ProgramThread programThread;

  public static void main(String[] args) {
    log.info("Starting program.");

    while (state.isBotStillRunning()) {
      MessageConnector messageConnector;
      try {
        messageConnector = new TwitchMessageConnector();
      } catch (IOException e) {
        log.fatal("Error creating messageConnector");
        waitToReconnect();
        continue;
      }
      programThread = new ProgramThread(messageConnector);
      Thread programThread = new Thread(ConsoleMain.programThread);
      programThread.start();
      try {
        programThread.join();
      } catch (InterruptedException ignored) {
      }

      if (state.isBotStillRunning()) {
        messageConnector.close();
        log.info("Attempting to reconnect in 1s...");
        waitToReconnect();
      }
    }
    log.info("Program shutdown.");
  }

  public static void waitToReconnect() {
    try {
      Thread.sleep(1000);
    } catch (InterruptedException ignored) {
    }
  }

  public static Instant getStartTime() {
    return startingTime;
  }

  public static void reconnect() {
    programThread.shutdown();
  }
}
