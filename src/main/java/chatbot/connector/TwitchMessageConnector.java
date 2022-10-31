package chatbot.connector;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class TwitchMessageConnector implements MessageConnector {

  private final BufferedReader bufferedReader;
  private final BufferedWriter bufferedWriter;
  private final Socket socket = new Socket("irc.chat.twitch.tv", 6667);

  public TwitchMessageConnector() throws IOException {
    socket.setKeepAlive(true);
    this.bufferedReader = new BufferedReader(
        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    this.bufferedWriter = new BufferedWriter(
        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
  }

  @Override
  public String getMessage() throws IOException {
    return bufferedReader.readLine();
  }

  @Override
  public void sendMessage(String message) throws IOException {
    bufferedWriter.write(message);
    bufferedWriter.flush();
  }

  @Override
  @SneakyThrows(IOException.class)
  public void close() {
    bufferedWriter.close();
    bufferedReader.close();
    socket.close();
  }
}
