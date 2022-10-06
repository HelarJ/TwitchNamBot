package ChatBot;

import ChatBot.StaticUtils.Config;
import ChatBot.StaticUtils.Running;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;

public class MainThread implements Runnable {
    private final Listener listener;
    private final Sender sender;
    private final CommandHandler stats;

    private final String channel;

    private final Thread listenerThread;
    private final Thread senderThread;
    private final Thread statisticsThread;
    private boolean running = true;

    public void shutdown() {
        running = false;
    }

    public MainThread(String[] args) throws IOException {
        this.channel = args[0];
        Socket socket = new Socket("irc.chat.twitch.tv", 6667);
        this.sender = new Sender(socket, new LinkedBlockingQueue<>(), channel);
        this.stats = new CommandHandler(channel, sender);
        this.listener = new Listener(socket, stats);
        listenerThread = new Thread(listener, "Listener");
        senderThread = new Thread(sender, "Sender");
        statisticsThread = new Thread(stats, "Statistics");
    }

    @Override
    public void run() {
        while (Running.getRunning() && running) {
            try {
                listenerThread.start();
                senderThread.start();
                statisticsThread.start();
                connect(sender, channel);
                statisticsThread.join();
                listenerThread.join();
                senderThread.join();
            } catch (IOException | InterruptedException e) {
                Running.getLogger().severe("Connection error: " + e.getMessage());
                closeThreads();
            } finally {
                try {
                    Thread.sleep(10000);
                    Running.getLogger().info("Restarting...");
                } catch (InterruptedException ignored) {
                }
            }
        }

        Running.getLogger().warning("Mainthread thread ended.");
    }

    public void closeThreads() {
        shutdown();
        listener.shutdown();
        sender.shutdown();
        stats.closeThreads();
        try {
            listenerThread.interrupt();
            listenerThread.join();
            senderThread.join();
            statisticsThread.join();
            Running.getLogger().info("Everything is shut down.");
        } catch (InterruptedException e) {
            Running.getLogger().info("Error closing threads.");
        }
    }

    public void connect(Sender sender, String channel) throws IOException {
        Running.getLogger().info("Starting server...");
        sender.sendToServer("PASS " + Config.getTwitchOauth() + "\r\n");
        sender.sendToServer("NICK " + Config.getTwitchUsername() + "\r\n");
        sender.sendToServer("USER nambot\r\n");
        sender.sendToServer("JOIN " + channel + "\r\n");
        sender.sendToServer("CAP REQ :twitch.tv/membership\r\n");
        sender.sendToServer("CAP REQ :twitch.tv/tags twitch.tv/commands\r\n");
        Running.getLogger().info("Credentials sent.");
    }
}
