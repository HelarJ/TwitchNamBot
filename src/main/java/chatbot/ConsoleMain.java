package chatbot;

import chatbot.singleton.SharedStateSingleton;
import chatbot.utils.Config;
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
    Config.initializeConfig();

    while (state.isBotStillRunning()) {
      try {
        programThread = new ProgramThread();
        Thread programThread = new Thread(ConsoleMain.programThread);
        programThread.start();
        programThread.join();
      } catch (IOException | InterruptedException e) {
        log.fatal("Exception in programthread: {}", e.getMessage());
        state.poisonQueues();
      } finally {
        if (state.isBotStillRunning()) {
          log.info("Attempting to reconnect...");
        }
      }
    }
  }

  public static Instant getStartTime() {
    return startingTime;
  }

  public static void reconnect() {
    programThread.shutdown();
  }
}
