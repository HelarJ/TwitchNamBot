package chatbot.utils;

import chatbot.ConsoleMain;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class Running {
    private static boolean running = true;
    private final static Logger logger = Logger.getLogger(Running.class.getName());
    private static final Instant startingTime = Instant.now();
    private static int messageCount = 0;
    private static int commandCount = 0;
    private static int timeoutCount = 0;
    private static int permabanCount = 0;
    public static List<String> blacklist = new ArrayList<>();
    public static List<String> textBlacklist = new ArrayList<>();
    public static String replacelist = "";

    //todo switch to a database based check instead of using local state.
    public static List<String> disabledUsers = new ArrayList<>();
    public static HashMap<String, List<String>> alts;
    public static HashMap<String, String> mains;

    /**
     * Is the stream online or offline. Modified by periodic API pulls.
     */
    public static boolean online;
    private static boolean first = true;

    public static Instant getStartTime() {
        return startingTime;
    }

    public static int getCommandCount() {
        return commandCount;
    }

    public synchronized static void addCommandCount() {
        commandCount++;
    }

    public static int getMessageCount() {
        return messageCount;
    }

    public synchronized static void addMessageCount() {
        messageCount++;
    }

    public static int getTimeoutCount() {
        return timeoutCount;
    }

    public static synchronized void addTimeoutCount() {
        timeoutCount++;
    }

    public static int getPermabanCount() {
        return permabanCount;
    }

    public synchronized static void addPermabanCount() {
        permabanCount++;
    }

    public static void startLog() {
        Date date = new Date();
        File file = new File("log");
        if (!file.exists()) {
            if (file.mkdir()) {
                logger.info("Log directory created successfully");
            }
        }
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        FileHandler fh;
        try {
            fh = new FileHandler("log/server_" + dateFormat.format(date) + ".log");
            fh.setFormatter(new SimpleFormatter());
            fh.publish(new LogRecord(Level.INFO, "Start of the server log."));
            fh.flush();
            fh.setLevel(Level.FINEST);
        } catch (IOException e) {
            logger.severe(e.getMessage());
            logger.severe("Could not create log file.");
            throw new RuntimeException("Could not create log file.");
        }
        logger.addHandler(fh);

    }

    public static void stop() {
        logger.info("Starting shutdown procedure.");
        running = false;
        ConsoleMain.reconnect();
    }

    public static boolean isBotStillRunning() {
        return running;
    }

    public static void setBlacklist(List<String> blacklist, List<String> textBlacklist, String replacelist) {
        Running.replacelist = replacelist;
        Running.blacklist = blacklist;
        Running.textBlacklist = textBlacklist;
    }

    public synchronized static void setOnline() {
        if (!online || first) {
            online = true;
            first = false;
            logger.info(Config.getChannelToJoin() + " is online.");
        }
    }

    public synchronized static void setOffline() {
        if (online || first) {
            online = false;
            first = false;
            logger.info(Config.getChannelToJoin() + " is offline.");
        }
    }

    public static String getAlts(String username) {
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
        if (Running.disabledUsers.contains(main)) {
            return "username:" + username.toLowerCase();
        }

        for (String alt : alts.get(main)) {
            if (!Running.disabledUsers.contains(alt)) {
                sb.append(" OR ");
                sb.append("username:");
                sb.append(alt);
            }
        }
        return sb.toString();
    }

}
