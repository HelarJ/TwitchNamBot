package ChatBot;

import ChatBot.Dataclass.Command;
import ChatBot.StaticUtils.Config;
import ChatBot.StaticUtils.Running;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.common.SolrInputDocument;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class MessageLogger implements Runnable {
    private final BlockingQueue<Command> logQueue;
    private final String SQLCredentials;
    private final String solrCredentials;
    private boolean online;
    private int lastid;
    private boolean running = true;
    private Instant lastCommit = Instant.now();
    private final List<SolrInputDocument> commitBacklog = new ArrayList<>();

    public void shutdown() {
        running = false;
    }

    public MessageLogger() {

        this.logQueue = new LinkedBlockingQueue<>();
        this.SQLCredentials = Config.getSQLCredentials();
        this.solrCredentials = Config.getSolrCredentials();
        getLastId();
    }

    @Override
    public void run() {
        Running.getLogger().info("MessageLogger thread started");
        while (Running.getRunning() && running) {
            try {
                Command command = logQueue.poll(3, TimeUnit.SECONDS);
                if (command != null) {
                    if (command.isWhisper()) {
                        recordWhisper(command);
                    } else {
                        lastid++;
                        recordMessage(command);
                    }
                }
            } catch (InterruptedException e) {
                Running.getLogger().warning("MessageLogger thread interrupted.");
            }
        }
        Running.getLogger().info("MessageLogger thread stopped");

    }

    public void getLastId() {
        try (Connection conn = DriverManager.getConnection(SQLCredentials);
             PreparedStatement stmt = conn.prepareStatement("call chat_stats.sp_get_last_id()"))
        {

            ResultSet rs = stmt.executeQuery();
            rs.next();
            this.lastid = rs.getInt("id");
        } catch (SQLException e) {
            Running.getLogger().severe("SQL ERROR: " + "SQLException: " + e.getMessage() + ", VendorError: " + e.getErrorCode());
        }
    }

    public void recordWhisper(Command command) {
        String username = command.getSender();
        String message = command.getMessage();
        String time = command.getTime();
        try (Connection conn = DriverManager.getConnection(SQLCredentials);
             PreparedStatement stmt = conn.prepareStatement("CALL chat_stats.sp_log_whisper(?,?,?);"))
        {
            stmt.setString(1, time);
            stmt.setString(2, username);
            stmt.setString(3, message);
            stmt.executeQuery();
        } catch (SQLException ex) {
            Running.getLogger().severe("SQL ERROR: " + "SQLException: " + ex.getMessage() + ", VendorError: " + ex.getErrorCode() + "\r\n WHISPER"
                    + username + " " + message);
        }
    }

    public void recordMessage(Command command) {
        String username = command.getSender();
        String userid = command.getUid();
        String message = command.getMessage();
        boolean subscribed = command.isSubscribed();
        String time = command.getTime();
        String fullMsg = command.getFullMsg();
        try (Connection conn = DriverManager.getConnection(SQLCredentials);
             PreparedStatement stmt = conn.prepareStatement("CALL chat_stats.sp_log_message_all(?,?,?,?,?,?,?,?);"))
        {
            stmt.setInt(1, lastid);
            stmt.setString(2, time);
            stmt.setString(3, username);
            stmt.setString(4, userid);
            stmt.setString(5, message);
            stmt.setBoolean(6, online);
            stmt.setBoolean(7, subscribed);
            stmt.setString(8, fullMsg);
            stmt.executeQuery();
        } catch (SQLException ex) {
            Running.getLogger().severe("SQL ERROR: " + "SQLException: " + ex.getMessage() + ", VendorError: " + ex.getErrorCode() + "\r\n"
                    + username + " " + userid + " " + message);
        }

        SolrInputDocument in = new SolrInputDocument();
        in.addField("id", lastid);
        in.addField("time", time);
        in.addField("username", username.toLowerCase());
        in.addField("message", message);
        commitBacklog.add(in);

        if (lastCommit.plus(10, ChronoUnit.SECONDS).isBefore(Instant.now())) {
            try (SolrClient solr = new HttpSolrClient.Builder(solrCredentials).build()) {
                solr.add(commitBacklog);
                solr.commit();
                commitBacklog.clear();
            } catch (IOException | SolrServerException | BaseHttpSolrClient.RemoteSolrException e) {
                Running.getLogger().severe("Solr error: " + e.getMessage());
            } finally {
                lastCommit = Instant.now();
            }
        }
        Running.addMessageCount();
    }

    public void setOnline() {
        online = true;
    }

    public void setOffline() {
        online = false;

    }
}
