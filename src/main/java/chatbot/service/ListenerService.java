package chatbot.service;

import chatbot.ConsoleMain;
import chatbot.message.CommandMessage;
import chatbot.message.LoggableMessage;
import chatbot.message.Message;
import chatbot.message.SimpleMessage;
import chatbot.message.TimeoutMessage;
import chatbot.singleton.SharedStateSingleton;
import chatbot.utils.Config;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

@Log4j2
public class ListenerService extends AbstractExecutionThreadService {
    private final BufferedReader bufferedReader;
    private final BufferedWriter bufferedWriter;
    private final SharedStateSingleton state = SharedStateSingleton.getInstance();
    private final CommandHandlerService commandHandlerService;
    private final String username;
    private final String admin;

    public ListenerService(Socket socket, CommandHandlerService commandHandlerService) throws IOException {
        this.commandHandlerService = commandHandlerService;
        this.bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        this.username = Config.getTwitchUsername().toLowerCase();
        this.admin = Config.getBotAdmin().toLowerCase();
    }
    @Override
    @SuppressWarnings("UnstableApiUsage")
    protected void triggerShutdown() {
        closeBuffered();
    }

    @Override
    protected void shutDown() {
        log.debug("{} stopped.", ListenerService.class);
    }

    @Override
    protected void startUp() {
        log.debug("{} started.", ListenerService.class);
    }

    @Override
    public void run() {
        while (state.isBotStillRunning()) {
            String output = getOutput();
            if (output == null) {
                return;
            }
            if (output.equals("PING :tmi.twitch.tv")) {
                handlePing();
                continue;
            }
            if (output.contains(".tmi.twitch.tv PRIVMSG")) {
                handleRegularMessage(output);
            } else if (output.contains(".tmi.twitch.tv WHISPER")) {
                handleWhisper(output);
            } else if (output.contains("CLEARCHAT")) {
                if (!output.contains("target-user-id")) {
                    log.info("Chat was cleared");
                    return;
                }
                handleTimeout(output);
            } else if (output.contains("USERNOTICE")) {
                handleSkipped(output);
            } else if (output.contains(".tv USERSTATE ")) {
                log.info("Message sent successfully.");
            } else if (output.contains(".tv CLEARMSG ")) {
                handleSkipped(output);
            } else if (output.contains(".tv PART ")) {
                handleSkipped(output);
            } else if (output.contains(".tv JOIN ")) {
                handleSkipped(output);
            } else {
                log.info(output);
            }
        }

        if (state.isBotStillRunning()) {
            ConsoleMain.reconnect();
        }
    }

    private void handleSkipped(String output) {
        log.trace(output);
    }

    private void handleTimeout(String output) {
        String name = output.substring(output.indexOf("#"));
        name = name.substring(name.indexOf(":") + 1);

        String userid = output.substring(output.indexOf("user-id="));
        userid = userid.substring(8, userid.indexOf(";"));

        int banTime = Integer.parseInt(output.substring(output.indexOf("=") + 1, output.indexOf(";")).strip());
        log.debug("{} timed out for {}s", name, banTime);
        if (banTime >= 121059319) {
            //todo move this and remove commandhandler dependency
            commandHandlerService.addDisabled(new CommandMessage("Autoban", name));
            state.messageLogBlockingQueue.add(new LoggableMessage(name, userid, "User was permanently banned.", false, false, output));
            state.increasePermabanCount();
        }
        state.timeoutBlockingQueue.add(new TimeoutMessage(name, userid, banTime));
    }

    private void handleWhisper(String output) {
        String name = output.substring(output.indexOf("display-name="));
        name = name.substring(13, name.indexOf(";"));
        String msg = output.substring(output.indexOf(username));
        msg = msg.substring(msg.indexOf(":") + 1);
        log.info("User {} whispered {}.", name, msg);
        Message message = new LoggableMessage(name, "NULL", msg, false, true, output);
        state.messageLogBlockingQueue.add(message);
        if (name.equalsIgnoreCase(admin)) {
            if (msg.equals("/shutdown")) {
                state.stop();
            } else if (msg.startsWith("/send ")) {
                msg = msg.substring(6);
                state.sendingBlockingQueue.add(new SimpleMessage(name, msg));
            } else if (msg.startsWith("/banuser ")) {
                log.info("Banned user {}", msg.substring(9));
            } else if (msg.equals("/restart")) {
                ConsoleMain.reconnect();
            }
        }
    }

    private void handleRegularMessage(String output) {
        String name = output.substring(0, output.indexOf(".tmi.twitch.tv PRIVMSG "));
        name = name.substring(name.lastIndexOf("@") + 1);

        String userid = output.substring(output.indexOf(";user-id="));
        userid = userid.substring(9);
        userid = userid.substring(0, userid.indexOf(";"));

        String subscriberStr = output.substring(output.indexOf("subscriber="));
        subscriberStr = subscriberStr.substring(11, subscriberStr.indexOf(";"));
        boolean subscribed = subscriberStr.equals("1");

        String message = output.substring(output.indexOf("PRIVMSG"));
        //String channelName = message.substring(message.indexOf("#"), message.indexOf(":")-1);
        String outputMSG = message.substring(message.indexOf(":") + 1);
        if (outputMSG.startsWith("\u0001ACTION ")) {
            outputMSG = outputMSG.replaceAll("\u0001", "");
            outputMSG = outputMSG.replaceFirst("ACTION", "/me");
        }
        state.messageLogBlockingQueue.add(new LoggableMessage(name, userid, outputMSG, subscribed, false, output));
        state.commandHandlerBlockingQueue.add(new CommandMessage(name, outputMSG));

        //records a timeout with a 0-second duration to prevent timeoutlist exploting.
        state.timeoutBlockingQueue.add(new TimeoutMessage(name, userid, 0));

    }

    private String getOutput() {
        String output = null;
        try {
            output = bufferedReader.readLine();
        } catch (IOException e) {
            log.fatal("Connection error for Listener: {}", e.getMessage());
            if (state.isBotStillRunning()) {
                ConsoleMain.reconnect();
            }
        }
        return output;
    }

    private void handlePing() {
        try {
            bufferedWriter.write("PONG :tmi.twitch.tv\r\n");
            bufferedWriter.flush();
        } catch (IOException e) {
            log.fatal("Error sending ping: {}", e.getMessage());
            if (state.isBotStillRunning()) {
                closeBuffered();
                ConsoleMain.reconnect();
            }
        }
    }

    @SneakyThrows(IOException.class)
    private void closeBuffered() {
        log.debug("Closing bufferedreader.");
        bufferedReader.close();
    }
}
