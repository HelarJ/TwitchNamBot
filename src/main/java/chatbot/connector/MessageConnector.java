package chatbot.connector;

import java.io.IOException;

public interface MessageConnector {

  /**
   * Reads a message from the connector. This operation blocks until there is a message.
   *
   * @return message as a string
   * @throws IOException when there is a connection issue with the connector.
   */
  String getMessage() throws IOException;

  /**
   * Sends a message to the connector.
   *
   * @param message Message to be sent as String.
   * @throws IOException when there is a connection issue with the connector.
   */
  void sendMessage(final String message) throws IOException;

  /**
   * Attempts to close the connections gracefully.
   */
  void close();
}
