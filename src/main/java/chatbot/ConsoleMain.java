package chatbot;

import chatbot.connector.MessageConnector;
import chatbot.connector.TwitchMessageConnector;
import chatbot.singleton.Config;
import chatbot.singleton.SharedState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.Instant;

public class ConsoleMain {

    private final static Logger log = LogManager.getLogger(ConsoleMain.class);

    private static final Instant startingTime = Instant.now();
    private static ProgramThread programThread;

    public static void main(String[] args) {
        log.info("Starting program.");
        Config.init();
        registerMetrics();

        while (SharedState.getInstance().isBotStillRunning()) {
            MessageConnector messageConnector;
            try {
                messageConnector = new TwitchMessageConnector();
            } catch (IOException e) {
                log.fatal("Error creating messageConnector", e);
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
            Config.init();
        }
        Metrics.close();
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
        Metrics.RECONNECT_COUNTER.inc();
        programThread.shutdown();
    }

    public static void registerMetrics() {
        log.info("Registering metrics...");
        Metrics.register();
    }
}
