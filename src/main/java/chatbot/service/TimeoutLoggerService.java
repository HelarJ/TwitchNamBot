package chatbot.service;

import chatbot.dao.db.Database;
import chatbot.message.Message;
import chatbot.message.PoisonMessage;
import chatbot.message.TimeoutMessage;
import chatbot.singleton.SharedState;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TimeoutLoggerService extends AbstractExecutionThreadService {

  private final static Logger log = LogManager.getLogger(TimeoutLoggerService.class);
  private final Database databaseHandler;
  private final SharedState state = SharedState.getInstance();

  public TimeoutLoggerService(Database database) {
    this.databaseHandler = database;
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
          log.error("Unexpected message type in timeout queue {}", message);
          continue;
        }
        if (timeout.getLength() > 0) {
          databaseHandler.addTimeout(timeout);
          state.increaseTimeoutCount();
        }
        boolean active = isTimeoutForUserAlreadyActive(timeout);

        // adding to timeout list is only active while the stream is offline.
        if (timeout.getLength() > 0 && !state.online.get() && !active) {
          state.timeouts.add(timeout);
        }
      }
      checkForExpiredTimeouts();
    }
  }

  private void checkForExpiredTimeouts() {
    for (TimeoutMessage timeout : state.timeouts) {
      if (timeout.hasExpired()) {
        if (state.timeouts.remove(timeout) && timeout.getLength() > 0) {
          databaseHandler.addNamListTimeout(timeout);
        }
      }
    }
  }

  private boolean isTimeoutForUserAlreadyActive(TimeoutMessage timeout) {
    for (TimeoutMessage tempTimeout : state.timeouts) {
      if (tempTimeout.getUsername().equalsIgnoreCase(timeout.getUsername())) {
        tempTimeout.resetTimeout(timeout.getLength());
        return true;
      }
    }
    return false;
  }
}
