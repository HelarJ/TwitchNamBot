package ChatBot.StaticUtils;

import ChatBot.ConsoleMain;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
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

    public static void addCommandCount() {
        commandCount++;
    }

    public static int getMessageCount() {
        return messageCount;
    }

    public static void addMessageCount() {
        messageCount++;
    }

    public static int getTimeoutCount() {
        return timeoutCount;
    }

    public static void addTimeoutCount() {
        timeoutCount++;
    }

    public static int getPermabanCount() {
        return permabanCount;
    }

    public static void addPermabanCount() {
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

    public static Logger getLogger() {
        return logger;
    }

    public static void setBlacklist(List<String> blacklist, List<String> textBlacklist, String replacelist) {
        Running.replacelist = replacelist;
        Running.blacklist = blacklist;
        Running.textBlacklist = textBlacklist;
    }

    public static void setOnline() {
        if (!online || first) {
            online = true;
            first = false;
            logger.info(Config.getChannelToJoin() + " is online.");
        }
    }

    public static void setOffline() {
        if (online || first) {
            online = false;
            first = false;
            logger.info(Config.getChannelToJoin() + " is offline.");
        }
    }
}
