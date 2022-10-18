package ChatBot.utils;

import ChatBot.dataclass.Message;
import ChatBot.dataclass.Timeout;
import ChatBot.enums.MessageType;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Holds blockingqueues for sharing data between threads. Initialized from the mainthread so these probably won't ever be null.
 */
public class SharedQueues {
    /**
     * Queue for commands.
     */
    public static BlockingQueue<Message> messageBlockingQueue;
    /**
     * Queue for sending messages to chat.
     */
    public static BlockingQueue<Message> sendingBlockingQueue;
    /**
     * Queue for database logging.
     */
    public static BlockingQueue<Message> messageLogBlockingQueue;

    public static BlockingQueue<Timeout> timeoutBlockingQueue;

    /**
     * Initializes shared blockingqueues with empty ones.
     */
    public static void initializeQueues() {
        messageBlockingQueue = new LinkedBlockingQueue<>();
        sendingBlockingQueue = new LinkedBlockingQueue<>();
        messageLogBlockingQueue = new LinkedBlockingQueue<>();
        timeoutBlockingQueue = new LinkedBlockingQueue<>();
    }

    public static void poisonQueues() {
        messageBlockingQueue.add(new Message(MessageType.POISON));
        sendingBlockingQueue.add(new Message(MessageType.POISON));
        messageLogBlockingQueue.add(new Message(MessageType.POISON));
        timeoutBlockingQueue.add(new Timeout(MessageType.POISON));
    }
}
