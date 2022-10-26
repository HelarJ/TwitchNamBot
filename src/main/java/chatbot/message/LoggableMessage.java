package chatbot.message;

import java.time.Instant;

public class LoggableMessage implements Message {

  private final String sender;
  private final String message;
  private final String uid;
  private final boolean subscribed;
  private final Instant time = Instant.now();
  private final boolean whisper;
  private final String fullMsg;

  public LoggableMessage(String sender, String uid, String message, boolean subscribed,
      boolean whisper, String fullMsg) {
    this.sender = sender;
    this.message = message;
    this.uid = uid;
    this.subscribed = subscribed;
    this.whisper = whisper;
    this.fullMsg = fullMsg;
  }

  public String getFullMsg() {
    return fullMsg;
  }

  @Override
  public String getSender() {
    return sender;
  }

  @Override
  public String getStringMessage() {
    return message;
  }

  public String getUid() {
    return uid;
  }

  public boolean isSubscribed() {
    return subscribed;
  }

  public String getTime() {
    String timeStr = time.toString().replaceAll("T", " ");
    if (timeStr.indexOf('.') == -1) {
      return timeStr;
    } else {
      return timeStr.substring(0, timeStr.indexOf('.'));
    }
  }

  public boolean isWhisper() {
    return whisper;
  }

  @Override
  public String toString() {
    return sender + ": " + message;
  }
}
