package chatbot.service;

import chatbot.dao.DatabaseHandler;
import chatbot.message.Message;
import chatbot.message.PoisonMessage;
import chatbot.message.TimeoutMessage;
import chatbot.singleton.SharedStateSingleton;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

@Log4j2
public class TimeoutLoggerService extends AbstractExecutionThreadService {
    private final ArrayList<TimeoutMessage> timeouts = new ArrayList<>();
    private final DatabaseHandler databaseHandler;
    private final SharedStateSingleton state = SharedStateSingleton.getInstance();

    public TimeoutLoggerService(DatabaseHandler databaseHandler) {
        this.databaseHandler = databaseHandler;
    }

    @Override
    protected void startUp() {
        log.debug("{} started.", TimeoutLoggerService.class);
    }

    @Override
    protected void shutDown() {
        log.debug("{} stopped.", TimeoutLoggerService.class);
    }

    @Override
    public void run() throws InterruptedException {

        while (state.isBotStillRunning()) {
            Message message = state.timeoutBlockingQueue.poll(3, TimeUnit.SECONDS);
            if (message != null) {
                if (message instanceof PoisonMessage) {
                    log.debug("{} poisoned.", TimeoutLoggerService.class);
                    break;
                }
                if (!(message instanceof TimeoutMessage timeout)) {
                    log.error("Unexcpected message type in timeoutqueue {}", message);
                    continue;
                }
                if (timeout.getLength() > 0) {
                    databaseHandler.addTimeout(timeout);
                    state.increaseTimeoutCount();
                }
                boolean active = isTimeoutForUserAlreadyActive(timeout);

                //timeout expiring/adding to timeoutlist only active while stream is offline.
                if (timeout.getLength() > 0 && !state.online.get() && !active) {
                    timeouts.add(timeout);
                }
            }

            checkForExpiredTimeouts();
        }
    }

    private void checkForExpiredTimeouts() {
        Iterator<TimeoutMessage> iterator = timeouts.iterator();
        while (iterator.hasNext()) {
            TimeoutMessage timeout = iterator.next();
            if (timeout.hasExpired()) {
                if (timeout.getLength() > 0) {
                    databaseHandler.addNamListTimeout(timeout);
                }
                iterator.remove();
            }
        }
    }

    private boolean isTimeoutForUserAlreadyActive(TimeoutMessage timeout) {
        for (TimeoutMessage temptimeout : timeouts) {
            if (temptimeout.getUsername().equals(timeout.getUsername())) {
                temptimeout.resetTimeout(timeout.getLength());
                return true;
            }
        }
        return false;
    }
}
