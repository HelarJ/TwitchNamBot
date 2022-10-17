package ChatBot.Service;

import ChatBot.Dataclass.Message;
import ChatBot.StaticUtils.Config;
import ChatBot.StaticUtils.Running;
import ChatBot.StaticUtils.SharedQueues;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
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
import java.util.logging.Logger;

public class MessageLoggerService extends AbstractExecutionThreadService {
    private final Logger logger = Logger.getLogger(CommandHandlerService.class.toString());
    private final String SQLCredentials;
    private final String solrCredentials;
    private Instant lastCommit = Instant.now();
    private final List<SolrInputDocument> commitBacklog = new ArrayList<>();

    public MessageLoggerService() {
        this.SQLCredentials = Config.getSQLCredentials();
        this.solrCredentials = Config.getSolrCredentials();
    }

    @Override
    protected void shutDown() {
        logger.info(MessageLoggerService.class + " stopped.");

    }

    @Override
    protected void startUp() {
        logger.info(MessageLoggerService.class + " started.");
    }

    @Override
    public void run() throws InterruptedException {
        while (Running.isBotStillRunning()) {
            Message message = SharedQueues.messageLogBlockingQueue.take();
            if (message.isPoison()) {
                logger.info(MessageLoggerService.class + " poisoned.");
                break;
            }

            logger.fine(MessageLoggerService.class + " received a message: " + message);
            if (message.isWhisper()) {
                recordWhisper(message);
            } else {
                recordMessage(message);
            }
        }
    }


    public void recordWhisper(Message command) {
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
            logger.severe("SQL ERROR: " + "SQLException: " + ex.getMessage() + ", VendorError: " + ex.getErrorCode() + "\r\n WHISPER"
                    + username + " " + message);
        }
    }

    public void recordMessage(Message command) {
        String username = command.getSender();
        String userid = command.getUid();
        String message = command.getMessage();
        boolean subscribed = command.isSubscribed();
        String time = command.getTime();
        String fullMsg = command.getFullMsg();
        int id = 0;
        try (Connection conn = DriverManager.getConnection(SQLCredentials);
             PreparedStatement stmt = conn.prepareStatement("SELECT chat_stats.sp_log_message_return_id(?,?,?,?,?,?,?);"))
        {
            stmt.setString(1, time);
            stmt.setString(2, username);
            stmt.setString(3, userid);
            stmt.setString(4, message);
            stmt.setBoolean(5, Running.online);
            stmt.setBoolean(6, subscribed);
            stmt.setString(7, fullMsg);
            ResultSet resultSet = stmt.executeQuery();
            resultSet.next();
            id = resultSet.getInt(1);
        } catch (SQLException ex) {
            logger.severe("SQL ERROR: " + "SQLException: " + ex.getMessage() + ", VendorError: " + ex.getErrorCode() + "\r\n"
                    + username + " " + userid + " " + message);
        }


        SolrInputDocument in = new SolrInputDocument();
        in.addField("id", id);
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
                logger.severe("Solr error: " + e.getMessage());
            } finally {
                lastCommit = Instant.now();
            }
        }

        Running.addMessageCount();
    }
}
