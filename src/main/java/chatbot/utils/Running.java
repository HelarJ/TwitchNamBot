package chatbot.utils;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class Running {
    private final static Logger logger = Logger.getLogger(Running.class.getName());
    private static final Instant startingTime = Instant.now();
    public static Instant getStartTime() {
        return startingTime;
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
}
