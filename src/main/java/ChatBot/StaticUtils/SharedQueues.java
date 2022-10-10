package ChatBot.StaticUtils;

import ChatBot.Dataclass.Command;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Holds blockingqueues for sharing data between threads. Initialized from the mainthread so these probably won't ever be null.
 */
public class SharedQueues {
    /**
     * Queue for commands.
     */
    public static BlockingQueue<Command> commandBlockingQueue;
    /**
     * Queue for sending messages to chat.
     */
    public static BlockingQueue<String> sendingBlockingQueue;
    /**
     * Queue for database logging.
     */
    public static BlockingQueue<Command> messageLogBlockingQueue;

    public static void initializeQueues() {
        commandBlockingQueue = new LinkedBlockingQueue<>();
        sendingBlockingQueue = new LinkedBlockingQueue<>();
        messageLogBlockingQueue = new LinkedBlockingQueue<>();
    }

    public static void poisonQueues() {
        //todo: add poison to shut down threads gracefully.
    }
}
