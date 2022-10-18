package ChatBot.Service;

import ChatBot.Dataclass.Message;
import ChatBot.StaticUtils.Running;
import ChatBot.StaticUtils.SharedQueues;
import ChatBot.dao.DatabaseHandler;
import ChatBot.dao.SQLSolrHandler;
import com.google.common.util.concurrent.AbstractExecutionThreadService;

import java.util.logging.Logger;

public class MessageLoggerService extends AbstractExecutionThreadService {
    private final Logger logger = Logger.getLogger(CommandHandlerService.class.toString());

    private final DatabaseHandler sqlSolrHandler = new SQLSolrHandler();

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
        while (Running.isBotStillRunning()) {
            Message message = SharedQueues.messageLogBlockingQueue.take();
            if (message.isPoison()) {
                logger.info(MessageLoggerService.class + " poisoned.");
                break;
            }
            logger.fine(MessageLoggerService.class + " received a message: " + message);

            if (message.isWhisper()) {
                sqlSolrHandler.recordWhisper(message);
            } else {
                sqlSolrHandler.recordMessage(message);
                Running.addMessageCount();
            }
        }
    }
}
