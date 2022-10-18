package chatbot;

import chatbot.utils.Config;
import chatbot.utils.Running;
import chatbot.utils.SharedStateSingleton;

import java.io.IOException;
import java.util.logging.Handler;
import java.util.logging.Logger;

public class ConsoleMain {
    private static final Logger logger = Logger.getLogger(ConsoleMain.class.toString());
    private static ProgramThread programThread;
    private static final SharedStateSingleton state = SharedStateSingleton.getInstance();

    public static void main(String[] args) throws IOException {
        Running.startLog();
        Config.initializeConfig();
        state.initializeQueues();
        SharedStateSingleton state = SharedStateSingleton.getInstance();

        while (state.isBotStillRunning()) {
            programThread = new ProgramThread();
            Thread programThread = new Thread(ConsoleMain.programThread);
            try {
                programThread.start();
                programThread.join();
            } catch (InterruptedException e) {
                logger.severe("Program thread interrupted. " + e.getMessage());
                state.poisonQueues();
            } finally {
                if (state.isBotStillRunning()) {
                    logger.info("Attempting to reconnect...");
                }
            }
        }
        for (Handler handler : logger.getHandlers()) {
            handler.close();
        }
    }

    public static void reconnect() {
        programThread.shutdown();
    }
}
