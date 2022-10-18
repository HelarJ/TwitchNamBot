package ChatBot.Service;

import ChatBot.Dataclass.Timeout;
import ChatBot.StaticUtils.Running;
import ChatBot.StaticUtils.SharedQueues;
import ChatBot.dao.DatabaseHandler;
import ChatBot.dao.SQLSolrHandler;
import com.google.common.util.concurrent.AbstractExecutionThreadService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class TimeoutLoggerService extends AbstractExecutionThreadService {
    private final Logger logger = Logger.getLogger(TimeoutLoggerService.class.toString());
    private final ArrayList<Timeout> timeouts;
    private final HashSet<String> usernames;
    private final DatabaseHandler databaseHandler;

    public TimeoutLoggerService() {
        this.usernames = new HashSet<>();
        timeouts = new ArrayList<>();
        databaseHandler = new SQLSolrHandler();
    }

    @Override
    protected void startUp() {
        logger.info(TimeoutLoggerService.class + " started.");
    }

    @Override
    protected void shutDown() {
        logger.info(TimeoutLoggerService.class + " stopped.");
    }

    @Override
    public void run() {

        while (Running.isBotStillRunning()) {
            try {
                Timeout timeout = SharedQueues.timeoutBlockingQueue.poll(3, TimeUnit.SECONDS);
                if (timeout != null) {
                    if (timeout.isPoison()) {
                        logger.info(TimeoutLoggerService.class + " poisoned.");
                        break;
                    }
                    if (timeout.getLength() > 0) {
                        databaseHandler.addTimeout(timeout);
                    }
                    Running.addTimeoutCount();
                    boolean active = isTimeoutForUserAlreadyActive(timeout);

                    //timeout expiring/adding to timeoutlist only active while stream is offline.
                    if (timeout.getLength() > 0 && !Running.online && !active) {
                        timeouts.add(timeout);
                    }
                }
            } catch (InterruptedException e) {
                logger.warning("Thread interrupted");
            }

            checkForExpiredTimeouts();
        }
    }

    private void checkForExpiredTimeouts() {
        Iterator<Timeout> iterator = timeouts.iterator();
        while (iterator.hasNext()) {
            Timeout timeout = iterator.next();
            if (timeout.hasExpired()) {
                addUsernameToDatabase(timeout);
                databaseHandler.addNamListTimeout(timeout);
                iterator.remove();
            }
        }
    }

    private boolean isTimeoutForUserAlreadyActive(Timeout timeout) {
        for (Timeout temptimeout : timeouts) {
            if (temptimeout.getUsername().equals(timeout.getUsername())) {
                temptimeout.resetTimeout(timeout.getLength());
                return true;
            }
        }
        return false;
    }

    private void addUsernameToDatabase(Timeout timeout) {
        if (usernames.contains(timeout.getUsername())) {
            return;
        }
        databaseHandler.addUsername(timeout);
        usernames.add(timeout.getUsername());
    }
}
