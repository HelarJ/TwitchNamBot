package ChatBot;

import ChatBot.Dataclass.Command;
import ChatBot.StaticUtils.Config;
import ChatBot.StaticUtils.Running;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.BlockingQueue;

public class Listener implements Runnable {
    private final BufferedReader bufferedReader;
    private final BufferedWriter bufferedWriter;
    private final CommandHandler commandHandler;
    private final String username;
    private final BlockingQueue<Command> statisticsQueue;
    private Instant lastPing;
    private final Thread closerThread;
    private boolean running = true;
    private final BlockingQueue<Command> messageQueue;
    private boolean plebsAllowed = true;
    private final String admin;

    public Listener(Socket socket, CommandHandler commandHandler) throws IOException {
        this.lastPing = Instant.now();
        this.commandHandler = commandHandler;
        this.bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        this.username = Config.getTwitchUsername().toLowerCase();
        this.admin = Config.getBotAdmin().toLowerCase();
        this.statisticsQueue = commandHandler.getStatisticsQueue();
        this.messageQueue = commandHandler.getMessageQueue();
        closerThread = new Thread(() -> {
            while (Running.getRunning() && running) {
                if (lastPing.plus(10, ChronoUnit.MINUTES).isBefore(Instant.now())) {
                    ConsoleMain.reconnect();
                    break;
                } else {
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException ignored) {
                        break;
                    }
                }
            }
        });
        closerThread.start();
    }

    public void shutdown() {
        running = false;
        closerThread.interrupt();
    }

    @Override
    public void run() {
        try {
            Running.getLogger().info("Listener started");
            String output;
            while (Running.getRunning() && (output = bufferedReader.readLine()) != null && running) {
                if (output.equals("PING :tmi.twitch.tv")) {
                    bufferedWriter.write("PONG :tmi.twitch.tv\r\n");
                    bufferedWriter.flush();
                    lastPing = Instant.now();
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
                    Command command = new Command(name, userid, outputMSG, subscribed, false, output);

                    messageQueue.add(command);
                    commandHandler.recordTimeout(name, userid, 0);
                    if (plebsAllowed || command.isSubscribed()) {
                        statisticsQueue.add(command);
                    }
                } else if (output.contains(".tmi.twitch.tv WHISPER")) {
                    //@badges=premium/1;color=#FF36F2;display-name=kroom;emotes=;message-id=2;thread-id=40206877_130928910;turbo=0;user-id=40206877;user-type= :kroom!kroom@kroom.tmi.twitch.tv WHISPER moonmoon_nam :test
                    String name = output.substring(output.indexOf("display-name="));
                    name = name.substring(13, name.indexOf(";"));
                    String msg = output.substring(output.indexOf(username));
                    msg = msg.substring(msg.indexOf(":") + 1);
                    Running.getLogger().info(String.format("User %s whispered %s.", name, msg));
                    Command command = new Command(name, "NULL", msg, false, true, output);
                    messageQueue.add(command);
                    if (name.equalsIgnoreCase(admin)) {
                        if (msg.equals("/setonline")) {
                            commandHandler.setOnline();
                        } else if (msg.equals("/setoffline")) {
                            commandHandler.setOffline();
                        } else if (msg.equals("/shutdown")) {
                            Running.stop();
                        } else if (msg.startsWith("/send ")) {
                            msg = msg.substring(6);
                            commandHandler.delegateMessage(msg);
                        } else if (msg.startsWith("/banuser ")) {
                            Running.getLogger().info("Banned user" + msg.substring(9));
                        } else if (msg.equals("/restart")) {
                            ConsoleMain.reconnect();
                        } else if (msg.equals("/unbanplebs")) {
                            this.plebsAllowed = true;
                            Running.getLogger().info("Unbanned plebs.");
                        } else if (msg.equals("/banplebs")) {
                            this.plebsAllowed = false;
                            Running.getLogger().info("Banned plebs.");
                        }
                    }
                } else if (output.contains("CLEARCHAT")) {
                    //@ban-duration=600;room-id=121059319;target-user-id=40206877;tmi-sent-ts=1588635742673 :tmi.twitch.tv CLEARCHAT #moonmoon :kroom
                    if (!output.contains("target-user-id")) {
                        Running.getLogger().info("Chat was cleared");
                        return;
                    }

                    String name = output.substring(output.indexOf("#"));
                    name = name.substring(name.indexOf(":") + 1);

                    String userid = output.substring(output.indexOf("user-id="));
                    userid = userid.substring(8, userid.indexOf(";"));

                    int banTime = Integer.parseInt(output.substring(output.indexOf("=") + 1, output.indexOf(";")).strip());
                    Running.getLogger().info(String.format("User %s timed out for %ds", name, banTime));
                    if (banTime >= 121059319) {
                        commandHandler.addDisabled("Autoban", name);
                        messageQueue.add(new Command(name, userid, "User was permanently banned.", false, false, output));
                        Running.addPermabanCount();
                    }
                    commandHandler.recordTimeout(name, userid, banTime);
                } else if (output.contains("USERNOTICE")) {
                    continue;
                    //String name = output.substring(output.indexOf("display-name="));
                    //name = name.substring(13, name.indexOf(";"));
                    //Running.getLogger().info(name + " subscribed.");

                } else if (output.contains(".tv USERSTATE ")) {
                    Running.getLogger().info("Message sent successfully.");
                } else if (output.contains(".tv CLEARMSG ")) {
                    continue;
                } else if (output.contains(".tv PART ")) {
                    continue;
                    //Running.getLogger().info(output);

                } else if (output.contains(".tv JOIN ")) {
                    continue;
                    //Running.getLogger().info(output);
                } else {
                    Running.getLogger().info(output);
                }
            }

            Running.getLogger().info("Listener Thread Ended.");
        } catch (IOException e) {
            Running.getLogger().warning("Connection error for Listener:" + e.getMessage());
        }
    }

}
