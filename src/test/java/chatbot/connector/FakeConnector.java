package chatbot.connector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import lombok.extern.log4j.Log4j2;

@Log4j2
@SuppressWarnings("all")
public class FakeConnector implements MessageConnector {

  BlockingQueue<String> fakeMessageQueue = new LinkedBlockingQueue<>();
  List<String> sentMessages = new ArrayList<>();

  public FakeConnector(List<String> fakeMessages) {
    fakeMessageQueue.addAll(fakeMessages);
  }

  @Override
  public String getMessage() throws IOException {
    try {
      return fakeMessageQueue.take();
    } catch (InterruptedException e) {
      throw new IOException(e);
    }
  }

  @Override
  public void sendMessage(String message) throws IOException {
    log.info(message);
    sentMessages.add(message);

  }

  public List<String> getSentMessages() {
    return sentMessages;
  }

  @Override
  public void close() {

  }
}
