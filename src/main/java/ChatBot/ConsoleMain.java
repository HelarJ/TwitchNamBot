package ChatBot;

import ChatBot.StaticUtils.Config;
import ChatBot.StaticUtils.Running;
import ChatBot.StaticUtils.SharedQueues;

import java.io.IOException;
import java.util.logging.Handler;
import java.util.logging.Logger;

public class ConsoleMain {
    private static final Logger logger = Logger.getLogger(ConsoleMain.class.toString());
    private static ProgramThread programThread;

    public static void main(String[] args) throws IOException {
        Running.startLog();
        Config.initializeConfig();
        SharedQueues.initializeQueues();

        while (Running.isBotStillRunning()) {
            programThread = new ProgramThread();
            Thread programThread = new Thread(ConsoleMain.programThread);
            try {
                programThread.start();
                programThread.join();
            } catch (InterruptedException e) {
                logger.severe("Program thread interrupted. " + e.getMessage());
                SharedQueues.poisonQueues();
            } finally {
                if (Running.isBotStillRunning()) {
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
