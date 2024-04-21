//package chatbot.connector;
//
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.concurrent.BlockingQueue;
//import java.util.concurrent.LinkedBlockingQueue;
//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;
//
//@SuppressWarnings("all")
//public class InMemoryConnector implements MessageConnector {
//  private final static Logger log = LogManager.getLogger(InMemoryConnector.class);
//
//  BlockingQueue<String> fakeMessageQueue = new LinkedBlockingQueue<>();
//  List<String> sentMessages = new ArrayList<>();
//
//  public InMemoryConnector(List<String> fakeMessages) {
//    fakeMessageQueue.addAll(fakeMessages);
//  }
//
//  @Override
//  public String getMessage() throws IOException {
//    try {
//      return fakeMessageQueue.take();
//    } catch (InterruptedException e) {
//      throw new IOException(e);
//    }
//  }
//
//  @Override
//  public void sendMessage(String message) throws IOException {
//    log.info(message);
//    sentMessages.add(message);
//
//  }
//
//  public List<String> getSentMessages() {
//    return sentMessages;
//  }
//
//  @Override
//  public void close() {
//
//  }
//}
