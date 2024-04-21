package chatbot;

import chatbot.connector.MessageConnector;
import chatbot.connector.TwitchMessageConnector;
import chatbot.singleton.SharedState;

import java.io.IOException;
import java.time.Instant;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ConsoleMain {

    private final static Logger log = LogManager.getLogger(ConsoleMain.class);

    private static final Instant startingTime = Instant.now();
    private static ProgramThread programThread;

    public static void main(String[] args) {
        log.info("Starting program.");

        while (SharedState.getInstance().isBotStillRunning()) {
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

            if (SharedState.getInstance().isBotStillRunning()) {
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
