package ChatBot;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.common.SolrInputDocument;

import java.io.IOException;
import java.sql.*;
import java.util.Properties;
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
    public void shutdown(){
        running = false;
    }


    public MessageLogger() {

        this.logQueue = new LinkedBlockingQueue<>();

        Properties p = Running.getProperties();
        this.SQLCredentials = String.format("jdbc:mariadb://%s:%s/%s?user=%s&password=%s", p.getProperty("db.ip"), p.getProperty("db.port"),
                p.getProperty("db.name"), p.getProperty("db.user"), p.getProperty("db.password"));
        this.solrCredentials = String.format("http://%s:%s/solr/%s", p.getProperty("solr.ip"),
                p.getProperty("solr.port"), p.getProperty("solr.core"));
        getLastId();
    }

    @Override
    public void run() {
        Running.getLogger().info("MessageLogger thread started");
        while (Running.getRunning() && running) {
            try {
                Command command = logQueue.poll(3, TimeUnit.SECONDS);
                if (command != null) {
                    if (command.isWhisper()){
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

    public void getLastId(){
        try (Connection conn = DriverManager.getConnection(SQLCredentials);
             PreparedStatement stmt = conn.prepareStatement("call chat_stats.sp_get_last_id()")) {

            ResultSet rs = stmt.executeQuery();
            rs.next();
            this.lastid = rs.getInt("id");
        } catch (SQLException e) {
            Running.getLogger().severe("SQL ERROR: " + "SQLException: " + e.getMessage() + ", VendorError: " + e.getErrorCode());
        }
    }

    public void recordWhisper(Command command){
        String username = command.getSender();
        String message = command.getMessage();
        String time = command.getTime();
        try (Connection conn = DriverManager.getConnection(SQLCredentials);
            PreparedStatement stmt = conn.prepareStatement("CALL chat_stats.sp_log_whisper(?,?,?);")) {
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
        try (Connection conn = DriverManager.getConnection(SQLCredentials);
             PreparedStatement stmt = conn.prepareStatement("CALL chat_stats.sp_log_message_all(?,?,?,?,?,?,?);")) {
            stmt.setInt(1, lastid);
            stmt.setString(2, time);
            stmt.setString(3, username);
            stmt.setString(4, userid);
            stmt.setString(5, message);
            stmt.setBoolean(6, online);
            stmt.setBoolean(7, subscribed);
            stmt.executeQuery();
        } catch (SQLException ex) {
            Running.getLogger().severe("SQL ERROR: " + "SQLException: " + ex.getMessage() + ", VendorError: " + ex.getErrorCode() + "\r\n"
                    + username + " " + userid + " " + message);
        }

        try (SolrClient solr = new HttpSolrClient.Builder(solrCredentials).build()){
            SolrInputDocument in = new SolrInputDocument();
            in.addField("id", lastid);
            in.addField("time", time);
            in.addField("username", username.toLowerCase());
            in.addField("message", message);
            solr.add(in);
            solr.commit();
        } catch (IOException | SolrServerException | BaseHttpSolrClient.RemoteSolrException e) {
            Running.getLogger().severe("Solr error: "+e.getMessage());
        }
        Running.addMessageCount();
    }

    public BlockingQueue<Command> getLogQueue() {
        return logQueue;
    }

    public void setOnline() {
        online = true;
    }

    public void setOffline() {
        online = false;

    }
}
