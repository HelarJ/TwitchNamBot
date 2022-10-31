package chatbot.service;

import chatbot.ConsoleMain;
import chatbot.dao.DatabaseHandler;
import chatbot.message.LoggableMessage;
import chatbot.message.Message;
import chatbot.message.PoisonMessage;
import chatbot.singleton.SharedStateSingleton;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class MessageLoggerService extends AbstractExecutionThreadService {

  private final DatabaseHandler databaseHandler;
  private final SharedStateSingleton state = SharedStateSingleton.getInstance();

  public MessageLoggerService(DatabaseHandler databaseHandler) {
    this.databaseHandler = databaseHandler;
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
      Message message = state.messageLogBlockingQueue.poll(10, TimeUnit.MINUTES);
      if (message == null) {
        log.error("10 minutes have passed without a message for messagelogger. " +
            "Assuming connection has been lost and reconnecting.");
        ConsoleMain.reconnect();
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
      log.trace("{} received a message: {}", MessageLoggerService.class, loggableMessage);
      if (loggableMessage.isWhisper()) {
        databaseHandler.recordWhisper(loggableMessage);
      } else {
        databaseHandler.recordMessage(loggableMessage);
        state.increaseMessageCount();
      }
    }
  }
}
