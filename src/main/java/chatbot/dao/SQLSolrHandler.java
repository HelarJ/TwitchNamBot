package chatbot.dao;

import chatbot.dataclass.Message;
import chatbot.dataclass.Timeout;
import chatbot.utils.Config;
import chatbot.utils.Running;
import chatbot.utils.Utils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

public class SQLSolrHandler implements DatabaseHandler {

    private static final Logger logger = Logger.getLogger(SQLSolrHandler.class.toString());
    private final String sqlCredentials = Config.getSQLCredentials();
    private final String solrCredentials = Config.getSolrCredentials();

    private Instant lastCommit = Instant.now();
    private final List<SolrInputDocument> commitBacklog = new ArrayList<>();

    public SQLSolrHandler() {
    }

    @Override
    public void addNamListTimeout(Timeout timeout) {
        try (Connection conn = DriverManager.getConnection(sqlCredentials);
             PreparedStatement stmt = conn.prepareStatement("select chat_stats.f_add_timeout(?,?);"))
        {
            String username = timeout.getUsername();
            int length = timeout.getLength();

            stmt.setString(1, username);
            stmt.setInt(2, length);
            ResultSet result = stmt.executeQuery();
            result.next();
            if (result.getBoolean(1)) {
                logger.info("Added " + username + " with a timeout of " + length + "s to db.");
            } else {
                logger.warning("Failed to add " + username + " with a timeout of " + length + "s to db.");
            }

        } catch (SQLException e) {
            logger.severe("SQLException: " + e.getMessage() + ", VendorError: " + e.getErrorCode());
        }
    }

    @Override
    public void addUsername(Timeout timeout) {
        try (Connection conn = DriverManager.getConnection(sqlCredentials);
             PreparedStatement stmt = conn.prepareStatement("select chat_stats.f_add_user(?);"))
        {
            stmt.setString(1, timeout.getUsername());
            stmt.executeQuery();
        } catch (SQLException ignored) {
        }
    }

    @Override
    public void addTimeout(Timeout timeout) {
        try (Connection conn = DriverManager.getConnection(sqlCredentials);
             PreparedStatement stmt = conn.prepareStatement("CALL chat_stats.sp_log_timeout(?,?,?,?);"))
        {
            stmt.setString(1, timeout.getUsername());
            stmt.setString(2, timeout.getUserid());
            stmt.setInt(3, timeout.getLength());
            stmt.setBoolean(4, Running.online);
            stmt.executeQuery();
        } catch (SQLException ex) {
            logger.severe("SQL ERROR: " + "SQLException: " + ex.getMessage() + ", VendorError: " + ex.getErrorCode());
        }
    }

    @Override
    public int getTimeoutAmount(String username) {
        try (Connection conn = DriverManager.getConnection(sqlCredentials);
             PreparedStatement stmt = conn.prepareStatement("call chat_stats.sp_get_usernam(?)"))
        {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            rs.next();
            return rs.getInt("timeout");

        } catch (SQLException ex) {
            logger.warning("SQL ERROR: " + "SQLException: " + ex.getMessage() + ", VendorError: " + ex.getErrorCode());
        }
        return 0;
    }

    @Override
    public void recordWhisper(Message command) {
        String username = command.getSender();
        String message = command.getStringMessage();
        String time = command.getTime();
        try (Connection conn = DriverManager.getConnection(sqlCredentials);
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

    @Override
    public void recordMessage(Message message) {
        int id = 0;
        try (Connection conn = DriverManager.getConnection(sqlCredentials);
             PreparedStatement stmt = conn.prepareStatement("SELECT chat_stats.sp_log_message_return_id(?,?,?,?,?,?,?);"))
        {
            stmt.setString(1, message.getTime());
            stmt.setString(2, message.getSender());
            stmt.setString(3, message.getUid());
            stmt.setString(4, message.getStringMessage());
            stmt.setBoolean(5, Running.online);
            stmt.setBoolean(6, message.isSubscribed());
            stmt.setString(7, message.getFullMsg());
            ResultSet resultSet = stmt.executeQuery();
            resultSet.next();
            id = resultSet.getInt(1);
        } catch (SQLException ex) {
            logger.severe("SQL ERROR: " + "SQLException: " + ex.getMessage() + ", VendorError: " + ex.getErrorCode() + "\r\n"
                    + message);
        }

        SolrInputDocument in = new SolrInputDocument();
        in.addField("id", id);
        in.addField("time", message.getTime());
        in.addField("username", message.getSender());
        in.addField("message", message.getStringMessage());
        commitBacklog.add(in);

        //Batch inserting as single inserts comes with very heavy disk useage.
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
    }

    @Override
    public String firstOccurrence(String from, String msg) {
        String phrase = Utils.getSolrPattern(msg);

        try (SolrClient solr = new HttpSolrClient.Builder(solrCredentials).build()) {
            SolrQuery query = new SolrQuery();
            query.set("q", phrase + " AND -message:\"!rs\" AND -message:\"!searchuser\" AND -message:\"!search\" AND -message:\"!rq\"");
            query.set("sort", "time asc");
            query.set("rows", 1);
            QueryResponse response = solr.query(query);
            if (response.getResults().getNumFound() == 0) {
                return "@" + from + ", no messages found PEEPERS";
            }
            SolrDocument result = response.getResults().get(0);
            String message = (String) result.getFirstValue("message");
            String msgName = (String) result.getFirstValue("username");
            if (Running.disabledUsers.contains(msgName.toLowerCase())) {
                msgName = "<redacted>";
            }
            Date date = (Date) result.getFirstValue("time");
            String dateStr = ("[" + date.toInstant().toString().replaceAll("T", " ").replaceAll("Z", "]"));
            return String.format("@%s, first occurrence: %s %s: %s", from, dateStr, Utils.addZws(msgName), message);

        } catch (IOException | BaseHttpSolrClient.RemoteSolrException | SolrServerException e) {
            logger.warning(e.getMessage());
            return "Internal error Deadlole";
        }
    }

    @Override
    public String randomSearch(String from, String username, String msg) {
        try (SolrClient solr = new HttpSolrClient.Builder(solrCredentials).build()) {
            SolrQuery query = new SolrQuery();
            String fullNameStr = Running.getAlts(username);
            query.set("q", fullNameStr);
            query.set("fq", Utils.getSolrPattern(msg) + " AND -message:\"!rs\" AND -message:\"!searchuser\" AND -message:\"!search\" AND -message:\"!rq\"");
            int seed = ThreadLocalRandom.current().nextInt(0, 999999999);
            query.set("sort", "random_" + seed + " asc");
            query.set("rows", 1);
            QueryResponse response = solr.query(query);
            if (response.getResults().getNumFound() == 0) {
                return "@" + from + ", no messages found PEEPERS";
            }
            SolrDocument result = response.getResults().get(0);
            String message = (String) result.getFirstValue("message");
            String msgName = (String) result.getFirstValue("username");
            Date date = (Date) result.getFirstValue("time");
            String dateStr = ("[" + date.toInstant().toString().replaceAll("T", " ").replaceAll("Z", "]"));
            return String.format("%s %s: %s", dateStr, Utils.addZws(msgName), message);

        } catch (IOException | BaseHttpSolrClient.RemoteSolrException | SolrServerException e) {
            logger.warning(e.getMessage());
            return "Internal error Deadlole";
        }
    }

    @Override
    public Map<String, String> getBlacklist() {
        Map<String, String> resultMap = new HashMap<>();

        try (Connection conn = DriverManager.getConnection(sqlCredentials);
             PreparedStatement stmt = conn.prepareStatement("call chat_stats.sp_get_blacklist()"))
        {
            stmt.execute();
            ResultSet rs = stmt.getResultSet();

            while (rs.next()) {
                String word = rs.getString("word");
                String type = rs.getString("type");
                resultMap.put(word, type);
            }
            logger.info("Blacklist pulled successfully " + resultMap.size() + " blacklisted words.");
        } catch (SQLException e) {
            logger.severe("SQLException: " + e.getMessage() + ", VendorError: " + e.getErrorCode());
        }
        return resultMap;
    }

    @Override
    public List<String> getDisabledList() {
        List<String> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(sqlCredentials);
             PreparedStatement stmt = conn.prepareStatement("call chat_stats.sp_get_disabled_users()"))
        {
            stmt.execute();
            ResultSet rs = stmt.getResultSet();
            while (rs.next()) {
                String user = rs.getString("username");
                list.add(user.toLowerCase());
            }
        } catch (SQLException e) {
            logger.severe("SQLException: " + e.getMessage() + ", VendorError: " + e.getErrorCode());
        }
        return list;
    }
}
