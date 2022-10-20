package chatbot.singleton;

import chatbot.ConsoleMain;
import chatbot.dataclass.Message;
import chatbot.dataclass.Timeout;
import chatbot.enums.MessageType;
import chatbot.utils.Config;
import lombok.extern.log4j.Log4j2;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Log4j2
public class SharedStateSingleton {
    private static final AtomicBoolean running = new AtomicBoolean(true);

    private final AtomicInteger messageCount = new AtomicInteger(0);
    private final AtomicInteger sentMessageCount = new AtomicInteger(0);
    private final AtomicInteger timeoutCount = new AtomicInteger(0);
    private final AtomicInteger permabanCount = new AtomicInteger(0);

    public AtomicBoolean online = new AtomicBoolean();
    private static boolean first = true;

    public CopyOnWriteArrayList<String> blacklist = new CopyOnWriteArrayList<>();
    public CopyOnWriteArrayList<String> textBlacklist = new CopyOnWriteArrayList<>();
    public AtomicReference<String> replacelist = new AtomicReference<>();
    public CopyOnWriteArrayList<String> disabledUsers = new CopyOnWriteArrayList<>();

    public HashMap<String, List<String>> alts = new HashMap<>();
    public HashMap<String, String> mains = new HashMap<>();

    private static SharedStateSingleton instance = new SharedStateSingleton();

    private SharedStateSingleton() {
    }

    public static SharedStateSingleton getInstance() {
        if (instance == null) {
            instance = new SharedStateSingleton();
        }
        return instance;
    }

    public int getMessageCount() {
        return messageCount.get();
    }

    public void increaseMessageCount() {
        messageCount.incrementAndGet();
    }

    public int getSentMessageCount() {
        return sentMessageCount.get();
    }

    public void increaseSentMessageCount() {
        sentMessageCount.incrementAndGet();
    }

    public int getTimeoutCount() {
        return timeoutCount.get();
    }

    public void increaseTimeoutCount() {
        timeoutCount.incrementAndGet();
    }

    public int getPermabanCount() {
        return permabanCount.get();
    }

    public void increasePermabanCount() {
        permabanCount.incrementAndGet();
    }

    public void setBlacklist(List<String> blacklist, List<String> textBlacklist, String replacelist) {
        this.replacelist.set(replacelist);
        this.blacklist.addAll(blacklist);
        this.textBlacklist.addAll(textBlacklist);
    }

    public void setDisabledUsers(List<String> disabledUsers) {
        this.disabledUsers.addAll(disabledUsers);
    }

    public String getAltsSolrString(String username) {
        username = username.toLowerCase();
        if (!mains.containsKey(username)) {
            return "username:" + username.toLowerCase();
        }
        String main = mains.get(username);
        if (main == null) {
            return "username:" + username.toLowerCase();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("username:");
        sb.append(main);
        if (disabledUsers.contains(main)) {
            return "username:" + username.toLowerCase();
        }

        for (String alt : alts.get(main)) {
            if (!disabledUsers.contains(alt)) {
                sb.append(" OR ");
                sb.append("username:");
                sb.append(alt);
            }
        }
        return sb.toString();
    }

    public void setOnline() {
        if (!online.get() || first) {
            online.set(true);
            first = false;
            log.info(Config.getChannelToJoin() + " is online.");
        }
    }

    public void setOffline() {
        if (online.get() || first) {
            online.set(false);
            first = false;
            log.info(Config.getChannelToJoin() + " is offline.");
        }
    }

    public boolean isBotStillRunning() {
        return running.get();
    }

    public void stop() {
        log.info("Starting shutdown procedure.");
        running.set(false);
        ConsoleMain.reconnect();
    }

    public BlockingQueue<Message> messageBlockingQueue;
    /**
     * Queue for sending messages to chat.
     */
    public BlockingQueue<Message> sendingBlockingQueue;
    /**
     * Queue for database logging.
     */
    public BlockingQueue<Message> messageLogBlockingQueue;

    public BlockingQueue<Timeout> timeoutBlockingQueue;

    /**
     * Initializes shared blockingqueues with empty ones.
     */
    public void initializeQueues() {
        messageBlockingQueue = new LinkedBlockingQueue<>();
        sendingBlockingQueue = new LinkedBlockingQueue<>();
        messageLogBlockingQueue = new LinkedBlockingQueue<>();
        timeoutBlockingQueue = new LinkedBlockingQueue<>();
    }

    public void poisonQueues() {
        log.info("Poisoning queues.");
        messageBlockingQueue.add(new Message(MessageType.POISON));
        sendingBlockingQueue.add(new Message(MessageType.POISON));
        messageLogBlockingQueue.add(new Message(MessageType.POISON));
        timeoutBlockingQueue.add(new Timeout(MessageType.POISON));
    }
}
