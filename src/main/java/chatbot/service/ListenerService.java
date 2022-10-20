package chatbot.service;

import chatbot.ConsoleMain;
import chatbot.dataclass.Message;
import chatbot.dataclass.Timeout;
import chatbot.singleton.SharedStateSingleton;
import chatbot.utils.Config;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
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
    private boolean running = true;
    private final String admin;

    public ListenerService(Socket socket, CommandHandlerService commandHandlerService) throws IOException {
        this.commandHandlerService = commandHandlerService;
        this.bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        this.username = Config.getTwitchUsername().toLowerCase();
        this.admin = Config.getBotAdmin().toLowerCase();
    }

    @Override
    protected void triggerShutdown() {
        running = false;
    }

    @Override
    public void run() {
        try {
            log.info("Listener started");
            String output;
            while (state.isBotStillRunning() && running && (output = bufferedReader.readLine()) != null) {
                if (output.equals("PING :tmi.twitch.tv")) {
                    bufferedWriter.write("PONG :tmi.twitch.tv\r\n");
                    bufferedWriter.flush();
                    continue;
                }
                if (output.contains(".tmi.twitch.tv PRIVMSG")) {
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
                    Message command = new Message(name, userid, outputMSG, subscribed, false, output);

                    state.messageLogBlockingQueue.add(command);

                    //records a timeout with a 0-second duration to prevent timeoutlist exploting.
                    state.timeoutBlockingQueue.add(new Timeout(name, userid, 0));
                    state.messageBlockingQueue.add(command);
                } else if (output.contains(".tmi.twitch.tv WHISPER")) {
                    //@badges=premium/1;color=#FF36F2;display-name=kroom;emotes=;message-id=2;thread-id=40206877_130928910;turbo=0;user-id=40206877;user-type= :kroom!kroom@kroom.tmi.twitch.tv WHISPER moonmoon_nam :test
                    String name = output.substring(output.indexOf("display-name="));
                    name = name.substring(13, name.indexOf(";"));
                    String msg = output.substring(output.indexOf(username));
                    msg = msg.substring(msg.indexOf(":") + 1);
                    log.info("User {} whispered {}.", name, msg);
                    Message message = new Message(name, "NULL", msg, false, true, output);
                    state.messageLogBlockingQueue.add(message);
                    if (name.equalsIgnoreCase(admin)) {
                        if (msg.equals("/shutdown")) {
                            state.stop();
                        } else if (msg.startsWith("/send ")) {
                            msg = msg.substring(6);
                            state.sendingBlockingQueue.add(new Message(msg));
                        } else if (msg.startsWith("/banuser ")) {
                            log.info("Banned user {}", msg.substring(9));
                        } else if (msg.equals("/restart")) {
                            ConsoleMain.reconnect();
                        }
                    }
                } else if (output.contains("CLEARCHAT")) {
                    //@ban-duration=600;room-id=121059319;target-user-id=40206877;tmi-sent-ts=1588635742673 :tmi.twitch.tv CLEARCHAT #moonmoon :kroom
                    if (!output.contains("target-user-id")) {
                        log.info("Chat was cleared");
                        return;
                    }

                    String name = output.substring(output.indexOf("#"));
                    name = name.substring(name.indexOf(":") + 1);

                    String userid = output.substring(output.indexOf("user-id="));
                    userid = userid.substring(8, userid.indexOf(";"));

                    int banTime = Integer.parseInt(output.substring(output.indexOf("=") + 1, output.indexOf(";")).strip());
                    log.info("User {} timed out for {}s", name, banTime);
                    if (banTime >= 121059319) {
                        //todo move this and remove commandhandler dependency
                        commandHandlerService.addDisabled("Autoban", name);
                        state.messageLogBlockingQueue.add(new Message(name, userid, "User was permanently banned.", false, false, output));
                        state.increasePermabanCount();
                    }
                    state.timeoutBlockingQueue.add(new Timeout(name, userid, banTime));
                } else if (output.contains("USERNOTICE")) {
                    continue;
                    //String name = output.substring(output.indexOf("display-name="));
                    //name = name.substring(13, name.indexOf(";"));
                    //logger.info(name + " subscribed.");

                } else if (output.contains(".tv USERSTATE ")) {
                    log.info("Message sent successfully.");
                } else if (output.contains(".tv CLEARMSG ")) {
                    continue;
                } else if (output.contains(".tv PART ")) {
                    continue;
                    //logger.info(output);

                } else if (output.contains(".tv JOIN ")) {
                    continue;
                    //logger.info(output);
                } else {
                    log.info(output);
                }
            }

            log.info("Listener Thread Ended.");
            if (state.isBotStillRunning()) {
                ConsoleMain.reconnect();
            }
        } catch (IOException e) {
            log.fatal("Connection error for Listener: {}", e.getMessage());
            if (state.isBotStillRunning()) {
                ConsoleMain.reconnect();
            }
        }
    }

}
