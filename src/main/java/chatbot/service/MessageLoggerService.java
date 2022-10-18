package chatbot.service;

import chatbot.dao.DatabaseHandler;
import chatbot.dataclass.Message;
import chatbot.utils.SharedStateSingleton;
import com.google.common.util.concurrent.AbstractExecutionThreadService;

import java.util.logging.Logger;

public class MessageLoggerService extends AbstractExecutionThreadService {
    private final Logger logger = Logger.getLogger(CommandHandlerService.class.toString());
    private final DatabaseHandler sqlSolrHandler;
    private final SharedStateSingleton state = SharedStateSingleton.getInstance();

    public MessageLoggerService(DatabaseHandler databaseHandler) {
        sqlSolrHandler = databaseHandler;
    }

    @Override
    protected void shutDown() {
        logger.info(MessageLoggerService.class + " stopped.");
    }

    @Override
    protected void startUp() {
        logger.info(MessageLoggerService.class + " started.");
    }

    @Override
    public void run() throws InterruptedException {
        while (state.isBotStillRunning()) {
            Message message = state.messageLogBlockingQueue.take();
            if (message.isPoison()) {
                logger.info(MessageLoggerService.class + " poisoned.");
                break;
            }
            logger.fine(MessageLoggerService.class + " received a message: " + message);

            if (message.isWhisper()) {
                sqlSolrHandler.recordWhisper(message);
            } else {
                sqlSolrHandler.recordMessage(message);
                state.increaseMessageCount();
            }
        }
    }
}
