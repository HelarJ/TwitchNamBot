package chatbot.singleton;

import chatbot.ConsoleMain;
import chatbot.message.Message;
import chatbot.message.PoisonMessage;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class SharedStateSingleton {

    private final ConfigSingleton config = ConfigSingleton.getInstance();
    private static final AtomicBoolean running = new AtomicBoolean(true);
    private static boolean first = true;
    private static SharedStateSingleton instance = new SharedStateSingleton();
    private final AtomicInteger messageCount = new AtomicInteger(0);
    private final AtomicInteger sentMessageCount = new AtomicInteger(0);
    private final AtomicInteger timeoutCount = new AtomicInteger(0);
    private final AtomicInteger permabanCount = new AtomicInteger(0);
    public AtomicBoolean online = new AtomicBoolean();
    public CopyOnWriteArrayList<String> blacklist = new CopyOnWriteArrayList<>();
    public CopyOnWriteArrayList<String> textBlacklist = new CopyOnWriteArrayList<>();
    public AtomicReference<String> replacelist = new AtomicReference<>();
    public CopyOnWriteArraySet<String> disabledUsers = new CopyOnWriteArraySet<>();
    public HashMap<String, List<String>> alts = new HashMap<>();
    public HashMap<String, String> mains = new HashMap<>();
    public HashMap<String, Integer> logCache = new HashMap<>();
    public BlockingQueue<Message> commandHandlerBlockingQueue = new LinkedBlockingQueue<>();
    /**
     * Queue for sending messages to chat.
     */
    public BlockingQueue<Message> sendingBlockingQueue = new LinkedBlockingQueue<>();
    /**
     * Queue for database logging.
     */
    public BlockingQueue<Message> messageLogBlockingQueue = new LinkedBlockingQueue<>();
    public BlockingQueue<Message> timeoutBlockingQueue = new LinkedBlockingQueue<>();

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

    public void setBlacklist(List<String> blacklist, List<String> textBlacklist,
        String replacelist) {
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

        for (String alt : alts.get(main)) {
            sb.append(" OR ");
            sb.append("username:");
            sb.append(alt);
        }
        return sb.toString();
    }

    public void setOnline() {
        if (!online.get() || first) {
            online.set(true);
            first = false;
            log.info(config.getChannelToJoin() + " is online.");
        }
    }

    public void setOffline() {
        if (online.get() || first) {
            online.set(false);
            first = false;
            log.info(config.getChannelToJoin() + " is offline.");
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

    public void poisonQueues() {
        log.info("Poisoning queues.");
        commandHandlerBlockingQueue.add(new PoisonMessage());
        sendingBlockingQueue.add(new PoisonMessage());
        messageLogBlockingQueue.add(new PoisonMessage());
        timeoutBlockingQueue.add(new PoisonMessage());
    }

    public void clearQueues() {
        commandHandlerBlockingQueue.clear();
        sendingBlockingQueue.clear();
        messageLogBlockingQueue.clear();
        timeoutBlockingQueue.clear();
    }
}
