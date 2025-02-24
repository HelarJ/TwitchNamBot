package chatbot.connector;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

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
    public void close() {
        try {
            bufferedWriter.close();
            bufferedReader.close();
            socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
