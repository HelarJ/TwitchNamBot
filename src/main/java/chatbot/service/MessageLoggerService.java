package chatbot.service;

import chatbot.dao.DatabaseHandler;
import chatbot.dataclass.Message;
import chatbot.singleton.SharedStateSingleton;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class MessageLoggerService extends AbstractExecutionThreadService {
    private final DatabaseHandler sqlSolrHandler;
    private final SharedStateSingleton state = SharedStateSingleton.getInstance();

    public MessageLoggerService(DatabaseHandler databaseHandler) {
        sqlSolrHandler = databaseHandler;
    }

    @Override
    protected void shutDown() {
        log.info("{} stopped.", MessageLoggerService.class);
    }

    @Override
    protected void startUp() {
        log.info("{} started.", MessageLoggerService.class);
    }

    @Override
    public void run() throws InterruptedException {
        while (state.isBotStillRunning()) {
            Message message = state.messageLogBlockingQueue.take();
            if (message.isPoison()) {
                log.info("{} poisoned.", MessageLoggerService.class);
                break;
            }
            log.trace("{} received a message: {}", MessageLoggerService.class, message);

            if (message.isWhisper()) {
                sqlSolrHandler.recordWhisper(message);
            } else {
                sqlSolrHandler.recordMessage(message);
                state.increaseMessageCount();
            }
        }
    }
}
