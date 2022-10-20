package chatbot;

import chatbot.singleton.SharedStateSingleton;
import chatbot.utils.Config;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.time.Instant;

@Log4j2
public class ConsoleMain {
    private static final Instant startingTime = Instant.now();
    private static ProgramThread programThread;

    private static final SharedStateSingleton state = SharedStateSingleton.getInstance();

    public static void main(String[] args) throws IOException, InterruptedException {
        log.info("Starting program.");
        Config.initializeConfig();
        state.initializeQueues();

        while (state.isBotStillRunning()) {
            programThread = new ProgramThread();
            Thread programThread = new Thread(ConsoleMain.programThread);
            programThread.start();
            programThread.join();

            if (state.isBotStillRunning()) {
                log.info("Attempting to reconnect...");
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
