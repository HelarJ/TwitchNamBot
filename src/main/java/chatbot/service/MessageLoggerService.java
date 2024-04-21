package chatbot.service;

import chatbot.dao.db.Database;
import chatbot.message.LoggableMessage;
import chatbot.message.Message;
import chatbot.message.PoisonMessage;
import chatbot.singleton.SharedState;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

public class MessageLoggerService extends AbstractExecutionThreadService {

    private final static Logger log = LogManager.getLogger(MessageLoggerService.class);

    private final Database database;
    private final SharedState state = SharedState.getInstance();

    public MessageLoggerService(Database database) {
        this.database = database;
    }

    @Override
    protected void shutDown() {
        log.debug("{} stopped.", MessageLoggerService.class);
    }

    @Override
    protected void startUp() {
        log.debug("{} started.", MessageLoggerService.class);
    }

    @Override
    public void run() throws InterruptedException {
        while (state.isBotStillRunning()) {
            Message message = state.messageLogBlockingQueue.poll(20, TimeUnit.MINUTES);
            if (message == null) {
                return;
            }

            if (message instanceof PoisonMessage) {
                log.debug("{} poisoned.", MessageLoggerService.class);
                break;
            }
            if (!(message instanceof LoggableMessage loggableMessage)) {
                log.error("Unexpected message type in messagelogger queue {}", message);
                continue;
            }
            state.lastMessageTime.set(Instant.now());
            log.trace("{} received a message: {}", MessageLoggerService.class, loggableMessage);
            if (loggableMessage.isWhisper()) {
                database.recordWhisper(loggableMessage);
            } else {
                database.recordMessage(loggableMessage);
                state.increaseMessageCount();
            }
        }
    }
}
