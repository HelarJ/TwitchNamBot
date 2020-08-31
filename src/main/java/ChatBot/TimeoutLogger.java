package ChatBot;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class TimeoutLogger implements Runnable {
    private final ArrayList<Timeout> timeouts;
    private final BlockingQueue<Timeout> timeoutQueue;
    private final String credentials;
    private final HashSet<String> usernames;
    private boolean running = true;

    public void shutdown(){
        running = false;
    }


    public TimeoutLogger(){
        this.timeoutQueue = new LinkedBlockingQueue<>();
        this.usernames = new HashSet<>();
        timeouts = new ArrayList<>();
        Properties p = Running.getProperties();
        this.credentials = String.format("jdbc:mariadb://%s:%s/%s?user=%s&password=%s", p.getProperty("db.ip"), p.getProperty("db.port"),
                p.getProperty("db.name"), p.getProperty("db.user"), p.getProperty("db.password"));
    }

    @Override
    public void run() {
        Running.getLogger().info("Timeoutlogger started.");
        while (Running.getRunning() && running){
            try {
                Timeout timeout = timeoutQueue.poll(3, TimeUnit.SECONDS);
                if (timeout != null) {
                    timeouts.add(timeout);
                }
            } catch (InterruptedException e) {
                Running.getLogger().warning("Thread interrupted");
            }

            Iterator<Timeout> iterator = timeouts.iterator();
            while (iterator.hasNext()){
                if (!Running.getRunning()){
                    Running.getLogger().info("Server shutting down but there are still timeouts left to be added: ");
                    while (iterator.hasNext()){
                        Running.getLogger().info(iterator.next().toString());
                        iterator.remove();
                    }
                    break;
                }
                Timeout t = iterator.next();
                if (t.hasExpired()){
                    try (Connection conn = DriverManager.getConnection(credentials);
                            PreparedStatement stmt = conn.prepareStatement("select chat_stats.f_add_user(?);")) {
                        if (!usernames.contains(t.getUsername())) { //buffer to prevent one name being added multiple times
                            stmt.setString(1, t.getUsername());
                            stmt.executeQuery();
                        }
                    } catch (SQLException ignored) {
                    } finally {
                        usernames.add(t.getUsername());
                    }
                    try (Connection conn = DriverManager.getConnection(credentials);
                         PreparedStatement stmt = conn.prepareStatement("select chat_stats.f_add_timeout(?,?);")){

                        String username = t.getUsername();
                        int length = t.getLength();

                        stmt.setString(1, username);
                        stmt.setInt(2, length);
                        ResultSet result = stmt.executeQuery();
                        result.next();
                        if (result.getBoolean(1)){
                            Running.getLogger().info("Added "+username+" with a timeout of "+length+"s to db.");
                        } else {
                            Running.getLogger().warning("Failed to add "+username+" with a timeout of "+length+"s to db.");
                        }

                    } catch (SQLException e) {
                        Running.getLogger().severe("SQLException: " + e.getMessage()+", VendorError: " + e.getErrorCode());
                    }
                    iterator.remove();
                }
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
            }
        }
        Running.getLogger().warning("Timeoutlogger stopped.");
    }

    public void addTimeout(String username, int length){
        for (Timeout timeout : timeouts) {
            if (timeout.getUsername().equals(username)){
                timeout.resetTimeout(length);
                return;
            }
        }
        timeoutQueue.add(new Timeout(username, length));

    }
}
