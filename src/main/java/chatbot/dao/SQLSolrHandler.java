package chatbot.dao;

import chatbot.message.LoggableMessage;
import chatbot.message.TimeoutMessage;
import chatbot.singleton.SharedStateSingleton;
import chatbot.utils.Config;
import chatbot.utils.HtmlBuilder;
import chatbot.utils.Utils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;

@Log4j2
public class SQLSolrHandler implements DatabaseHandler {

  private final String solrCredentials = Config.getSolrCredentials();
  private final List<SolrInputDocument> commitBacklog = new ArrayList<>();
  private final SharedStateSingleton state = SharedStateSingleton.getInstance();
  private final BasicDataSource dataSource = new BasicDataSource();
  private Instant lastCommit = Instant.now();

  public SQLSolrHandler() {
    dataSource.setUrl(Config.getSqlUrl());
    dataSource.setUsername(Config.getSqlUsername());
    dataSource.setPassword(Config.getSqlPassword());
    dataSource.setInitialSize(5);
    dataSource.setMinIdle(5);
    dataSource.setMaxIdle(20);
  }

  @Override
  public void addNamListTimeout(TimeoutMessage timeout) {
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement("call chat_stats.sp_add_timeout(?,?);")) {
      String username = timeout.getUsername();
      int length = timeout.getLength();

      stmt.setString(1, username);
      stmt.setInt(2, length);
      stmt.executeQuery();
      log.info("Added {} with a timeout of {}s to db.", username, length);

    } catch (SQLException e) {
      log.error("SQLException: {}, VendorError: {}", e.getMessage(), e.getErrorCode());
    }
  }

  @Override
  public int getMessageCount(String username) {
    String stmtStr = "call chat_stats.sp_get_message_count(?)";
    if (username.equalsIgnoreCase("all")) {
      stmtStr = "call chat_stats.sp_get_all_count(?)";
    }
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(stmtStr)) {
      stmt.setString(1, username);
      ResultSet rs = stmt.executeQuery();
      if (rs.next()) {
        return rs.getInt("count");
      }
    } catch (SQLException e) {
      log.error("SQLException: {}, VendorError: {}", e.getMessage(), e.getErrorCode());
    }
    return 0;
  }

  @Override
  public List<String> getModList() {
    List<String> modList = new ArrayList<>();
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement("call chat_stats.sp_get_mods()")) {
      stmt.execute();
      ResultSet rs = stmt.getResultSet();

      while (rs.next()) {
        String name = rs.getString("name");
        modList.add(name);
      }

    } catch (SQLException e) {
      log.error("SQLException: {}, VendorError: {}", e.getMessage(), e.getErrorCode());
    }
    return modList;
  }

  @Override
  public List<String> getAltsList() {
    List<String> csvList = new ArrayList<>();
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement("call chat_stats.sp_get_alts()")) {
      stmt.execute();
      ResultSet rs = stmt.getResultSet();
      while (rs.next()) {
        String main = rs.getString("main").toLowerCase();
        String alt = rs.getString("alt").toLowerCase();
        csvList.add("%s,%s".formatted(main, alt));
      }
    } catch (SQLException e) {
      log.error("SQLException: {}, VendorError: {}", e.getMessage(), e.getErrorCode());
    }
    return csvList;
  }

  @Override
  public void addTimeout(TimeoutMessage timeout) {
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(
            "CALL chat_stats.sp_log_timeout(?,?,?,?);")) {
      stmt.setString(1, timeout.getUsername());
      stmt.setString(2, timeout.getUserid());
      stmt.setInt(3, timeout.getLength());
      stmt.setBoolean(4, state.online.get());
      stmt.executeQuery();
    } catch (SQLException e) {
      log.error("SQLException: {}, VendorError: {}", e.getMessage(), e.getErrorCode());
    }
  }

  @Override
  public int getTimeoutAmount(String username) {
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement("call chat_stats.sp_get_usernam(?)")) {
      stmt.setString(1, username);
      ResultSet rs = stmt.executeQuery();
      rs.next();
      return rs.getInt("timeout");

    } catch (SQLException e) {
      log.error("SQLException: {}, VendorError: {}", e.getMessage(), e.getErrorCode());
    }
    return 0;
  }

  @Override
  public String getTopTimeouts() {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      ResultSet results = stmt.executeQuery("call chat_stats.sp_get_top10to()");
      StringBuilder sb = new StringBuilder();
      sb.append("Top NaMmers: ");
      while (results.next()) {
        sb.append("%s: %s | ".formatted(results.getString("username"),
            Utils.convertTime(results.getInt("timeout"))));
        if (sb.length() >= 270) {
          break;
        }
      }

      return sb.toString();
    } catch (SQLException e) {
      log.error("SQLException: {}, VendorError: {}", e.getMessage(), e.getErrorCode());
      return null;
    }
  }

  @Override
  public void recordWhisper(LoggableMessage command) {
    String username = command.getSender();
    String message = command.getStringMessage();
    String time = command.getTime();
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement("CALL chat_stats.sp_log_whisper(?,?,?);")) {
      stmt.setString(1, time);
      stmt.setString(2, username);
      stmt.setString(3, message);
      stmt.executeQuery();
    } catch (SQLException e) {
      log.error("SQLException: {}, VendorError: {}", e.getMessage(), e.getErrorCode());
    }
  }

  @Override
  public void recordMessage(LoggableMessage message) {
    int id = 0;
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(
            "SELECT chat_stats.sp_log_message_return_id(?,?,?,?,?,?,?);")) {
      stmt.setString(1, message.getTime());
      stmt.setString(2, message.getSender());
      stmt.setString(3, message.getUid());
      stmt.setString(4, message.getStringMessage());
      stmt.setBoolean(5, state.online.get());
      stmt.setBoolean(6, message.isSubscribed());
      stmt.setString(7, message.getFullMsg());
      ResultSet resultSet = stmt.executeQuery();
      resultSet.next();
      id = resultSet.getInt(1);
    } catch (SQLException e) {
      log.error("SQLException: {}, VendorError: {}", e.getMessage(), e.getErrorCode());
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
        log.error("Solr error: {}", e.getMessage());
      } finally {
        lastCommit = Instant.now();
      }
    }
  }

  @Override
  public String firstOccurrence(String msg) {
    String phrase = Utils.getSolrPattern(msg);

    try (SolrClient solr = new HttpSolrClient.Builder(solrCredentials).build()) {
      SolrQuery query = new SolrQuery();
      query.set("q", phrase
          + " AND -message:\"!rs\" AND -message:\"!searchuser\" AND -message:\"!search\" AND -message:\"!rq\"");
      query.set("sort", "time asc");
      query.set("rows", 1);
      QueryResponse response = solr.query(query);
      if (response.getResults().getNumFound() == 0) {
        return "no messages found PEEPERS";
      }
      SolrDocument result = response.getResults().get(0);
      String message = (String) result.getFirstValue("message");
      String msgName = (String) result.getFirstValue("username");
      if (state.disabledUsers.stream().anyMatch(msgName::equalsIgnoreCase)) {
        msgName = "<redacted>";
      }
      Date date = (Date) result.getFirstValue("time");
      String dateStr = ("[" + date.toInstant().toString().replaceAll("T", " ")
          .replaceAll("Z", "]"));
      return String.format("first occurrence: %s %s: %s", dateStr, msgName, message);

    } catch (IOException | BaseHttpSolrClient.RemoteSolrException | SolrServerException e) {
      log.error(e.getMessage());
      return "Internal error Deadlole";
    }
  }

  @Override
  public String firstMessage(String username) {
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement("call chat_stats.sp_get_first_message(?)")) {
      stmt.setString(1, username);
      ResultSet rs = stmt.executeQuery();
      rs.next();
      String convertedTime = Utils.convertTime((int) (Instant.now().minus(rs.getTimestamp("time",
              Calendar.getInstance(TimeZone.getTimeZone("UTC"))).getTime(),
          ChronoUnit.MILLIS).toEpochMilli() / 1000));
      return String.format("%s's first message %s ago was: %s",
          username,
          convertedTime,
          rs.getString("message"));
    } catch (SQLException e) {

      log.error("SQLException: {}, VendorError: {}", e.getMessage(), e.getErrorCode());
      return "internal server error Deadlole";
    }
  }

  @Override
  public String lastMessage(String username) {
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement("call chat_stats.sp_get_last_message(?)")) {
      stmt.setString(1, username);
      ResultSet rs = stmt.executeQuery();
      rs.next();
      String convertedTime = Utils.convertTime((int) (Instant.now().minus(rs.getTimestamp("time",
              Calendar.getInstance(TimeZone.getTimeZone("UTC"))).getTime(),
          ChronoUnit.MILLIS).toEpochMilli() / 1000));
      String message = rs.getString("message");
      return String.format("%s's last message %s ago was: %s",
          username,
          convertedTime,
          message);
    } catch (SQLException e) {
      log.error("SQLException: {}, VendorError: {}", e.getMessage(), e.getErrorCode());
      return "internal server error Deadlole";
    }
  }

  @Override
  public String randomSearch(String username, String msg) {
    try (SolrClient solr = new HttpSolrClient.Builder(solrCredentials).build()) {
      SolrQuery query = new SolrQuery();
      String fullNameStr = state.getAltsSolrString(username);
      query.set("q", fullNameStr);
      query.set("fq", Utils.getSolrPattern(msg)
          + " AND -message:\"!rs\" AND -message:\"!searchuser\" AND -message:\"!search\" AND -message:\"!rq\"");
      int seed = ThreadLocalRandom.current().nextInt(0, 999999999);
      query.set("sort", "random_" + seed + " asc");
      query.set("rows", 1);
      QueryResponse response = solr.query(query);
      if (response.getResults().getNumFound() == 0) {
        return "no messages found PEEPERS";
      }
      SolrDocument result = response.getResults().get(0);
      String message = (String) result.getFirstValue("message");
      String msgName = (String) result.getFirstValue("username");
      Date date = (Date) result.getFirstValue("time");
      String dateStr = ("[" + date.toInstant().toString().replaceAll("T", " ")
          .replaceAll("Z", "]"));
      return String.format("%s %s: %s", dateStr, msgName, message);

    } catch (IOException | BaseHttpSolrClient.RemoteSolrException | SolrServerException e) {
      log.error(e.getMessage());
      return "internal error Deadlole";
    }
  }

  @Override
  public String randomQuote(String username, String year) {
    try (SolrClient solr = new HttpSolrClient.Builder(solrCredentials).build()) {
      SolrQuery query = new SolrQuery();
      String fullNameStr = state.getAltsSolrString(username);
      query.set("q", fullNameStr);
      if (year != null) {
        query.set("fq",
            "-message:\"!rq\" AND -message:\"!chain\" AND -message:\"!lastmessage\" AND time:"
                + year);
      } else {
        query.set("fq", "-message:\"!rq\" AND -message:\"!chain\" AND -message:\"!lastmessage\"");
      }
      int seed = ThreadLocalRandom.current().nextInt(0, 9999999);
      query.set("sort", "random_" + seed + " asc");
      int amount = 50;
      query.set("rows", amount);
      QueryResponse response = solr.query(query);
      SolrDocumentList results = response.getResults();

      if (results.getNumFound() == 0) {
        log.info("Did not find any messages for " + fullNameStr);
        return "no messages found PEEPERS";
      }
      SolrDocument result = results.get(0);
      String message = (String) result.getFirstValue("message");
      Date date = (Date) result.getFirstValue("time");
      username = (String) result.getFirstValue("username");
      String dateStr = ("[" + date.toInstant().toString().replaceAll("T", " ")
          .replaceAll("Z", "]"));
      return String.format("%s %s: %s",
          dateStr,
          username,
          message);
    } catch (IOException | SolrServerException e) {
      log.error("Solr error: " + e.getMessage());
      return "internal error Deadlole";
    }
  }

  @Override
  public String getLogs(String username, int count) {
    Instant startTime = Instant.now();
    try (Connection conn = dataSource.getConnection()) {
      PreparedStatement stmt;
      if (username.equalsIgnoreCase("all")) {
        stmt = conn.prepareStatement("CALL chat_stats.sp_get_logs();");
      } else {
        stmt = conn.prepareStatement("CALL chat_stats.sp_get_user_logs(?);");
        stmt.setString(1, username);
      }
      ResultSet rs = stmt.executeQuery();
      Instant queryEndTime = Instant.now();
      log.info("Query took: " + (Utils.convertTime(
          (int) (queryEndTime.minus(startTime.toEpochMilli(), ChronoUnit.MILLIS).toEpochMilli()
              / 1000))));

      java.util.Date date = new Date();
      SimpleDateFormat generationDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      generationDate.setTimeZone(TimeZone.getTimeZone("UTC"));
      rs.last();
      int currentCount = rs.getRow();
      rs.beforeFirst();
      String html = new HtmlBuilder()
          .withTitle("Logs for %s".formatted(username))
          .withBodyIntro("""
              Logs for %s in the channel %s.<br>
              %d messages listed out of %d total.<br>
              Generated %s. All times UTC.<br>
              """.formatted(username, Config.getChannelToJoin(),
              currentCount, count,
              generationDate.format(date)))
          .withBodyContent(getLogsInternal(rs)).build();

      log.info("Data compilation took: " + (Utils.convertTime(
          (int) (Instant.now().minus(queryEndTime.toEpochMilli(), ChronoUnit.MILLIS).toEpochMilli()
              / 1000))));
      return html;

    } catch (SQLException e) {
      log.error("SQLException: {}, VendorError: {}", e.getMessage(), e.getErrorCode());
      return null;
    }
  }

  private List<String> getLogsInternal(ResultSet rs) throws SQLException {
    List<String> content = new ArrayList<>();
    while (rs.next()) {
      String time = rs.getTimestamp("time").toString();
      time = "[" + time.substring(0, time.length() - 2) + "]";
      String username = new String(rs.getBytes("username"), StandardCharsets.UTF_8);
      String message = new String(rs.getBytes("message"), StandardCharsets.UTF_8).
          replaceAll("&", "&amp;").
          replaceAll("<", "&lt;").
          replaceAll(">", "&gt;");

      content.add("%s %s: %s<br>\n".formatted(time, username, message));
    }
    return content;
  }

  @Override
  public long search(String msg) {
    String phrase = Utils.getSolrPattern(msg);
    try (SolrClient solr = new HttpSolrClient.Builder(solrCredentials).build()) {
      SolrQuery query = new SolrQuery();
      query.set("q", phrase + " AND -username:" + Config.getTwitchUsername()
          + " AND -message:\"!search\" AND -message:\"!searchuser\" AND -message:\"!rs\"");
      query.set("rows", 1);
      QueryResponse response = solr.query(query);
      SolrDocumentList result = response.getResults();
      return result.getNumFound();

    } catch (IOException | BaseHttpSolrClient.RemoteSolrException | SolrServerException e) {
      log.error(e.getMessage());
      return 0;
    }
  }

  @Override
  public long searchUser(String username, String msg) {
    String phrase = Utils.getSolrPattern(msg);
    try (SolrClient solr = new HttpSolrClient.Builder(solrCredentials).build()) {
      SolrQuery query = new SolrQuery();
      query.set("q",
          phrase + " AND username:" + username + " AND -username:" + Config.getTwitchUsername()
              + " AND -message:\"!search\" AND -message:\"!searchuser\" AND -message:\"!rs\"");
      query.set("rows", 1);
      QueryResponse response = solr.query(query);
      SolrDocumentList result = response.getResults();
      return result.getNumFound();
    } catch (IOException | SolrServerException | BaseHttpSolrClient.RemoteSolrException e) {
      log.error(e.getMessage());
      return 0;
    }
  }

  @SuppressWarnings("unused")
  public long searchTotalWords(String word) {
    try (SolrClient solr = new HttpSolrClient.Builder(solrCredentials).build()) {
      SolrQuery query = new SolrQuery();
      query.set("fl", "*,ttf(message," + word + ")");
      query.set("rows", 1);
      QueryResponse response = solr.query(query);
      SolrDocumentList result = response.getResults();
      long resultCount = result.getNumFound();
      if (resultCount == 0) {
        return 0;
      }

      SolrDocument entries = response.getResults().get(0);
      return (long) entries.get("ttf(message," + word + ")");

    } catch (IOException | BaseHttpSolrClient.RemoteSolrException | SolrServerException e) {
      log.error(e.getMessage());
      return 0;
    }
  }

  @Override
  public Map<String, String> getBlacklist() {
    Map<String, String> resultMap = new HashMap<>();

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement("call chat_stats.sp_get_blacklist()")) {
      stmt.execute();
      ResultSet rs = stmt.getResultSet();

      while (rs.next()) {
        String word = rs.getString("word");
        String type = rs.getString("type");
        resultMap.put(word, type);
      }
    } catch (SQLException e) {
      log.error("SQLException: {}, VendorError: {}", e.getMessage(), e.getErrorCode());
    }
    return resultMap;
  }

  @Override
  public List<String> getDisabledList() {
    List<String> list = new ArrayList<>();
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement("call chat_stats.sp_get_disabled_users()")) {
      stmt.execute();
      ResultSet rs = stmt.getResultSet();
      while (rs.next()) {
        String user = rs.getString("username");
        list.add(user);
      }
    } catch (SQLException e) {
      log.error("SQLException: {}, VendorError: {}", e.getMessage(), e.getErrorCode());
    }
    return list;
  }

  @Override
  public void addDisabled(String from, String username) {
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement("CALL chat_stats.sp_add_disabled(?,?);")) {
      stmt.setString(1, from);
      stmt.setString(2, username);
      stmt.executeQuery();

    } catch (SQLException e) {
      log.error("SQLException: {}, VendorError: {}", e.getMessage(), e.getErrorCode());
    }
  }

  @Override
  public void removeDisabled(String username) {
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement("CALL chat_stats.sp_remove_disabled(?);")) {
      stmt.setString(1, username);
      stmt.executeQuery();

    } catch (SQLException e) {
      log.error("SQLException: {}, VendorError: {}", e.getMessage(), e.getErrorCode());
    }
  }

  @Override
  public List<String> getAlternateNames(String username) {
    List<String> names = new ArrayList<>();
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement("call chat_stats.sp_get_names(?)")) {
      stmt.setString(1, username.toLowerCase());
      ResultSet rs = stmt.executeQuery();

      while (rs.next()) {
        String name = rs.getString("username");
        if (!name.equalsIgnoreCase(username)) {
          names.add(name);
        }
      }
    } catch (SQLException e) {
      log.error("SQLException: {}, VendorError: {}", e.getMessage(), e.getErrorCode());
    }
    return names;
  }

  @Override
  public boolean addAlt(String main, String alt) {
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement("CALL chat_stats.sp_add_alt(?,?);")) {
      stmt.setString(1, main);
      stmt.setString(2, alt);
      stmt.executeQuery();
      return true;
    } catch (SQLException e) {
      log.error("SQLException: {}, VendorError: {}", e.getMessage(), e.getErrorCode());
    }
    return false;
  }

  @Override
  public void setCommandPermissionUser(String user, String command, boolean enable) {
    HashMap<String, Boolean> permissions = getPersonalPermissions(user);
    permissions.put(command, enable);
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(
            "CALL chat_stats.sp_set_user_permissions(?,?);")) {
      stmt.setString(1, user);
      stmt.setString(2, new ObjectMapper().writeValueAsString(permissions));
      stmt.execute();

    } catch (SQLException e) {
      log.error("SQLException: {}, VendorError: {}", e.getMessage(), e.getErrorCode());
    } catch (JsonProcessingException e) {
      log.error("Error processing map to json: {}", e.getMessage());
    }
  }

  @Nonnull
  @Override
  public HashMap<String, Boolean> getPersonalPermissions(String user) {
    HashMap<String, Boolean> permissionMap = new HashMap<>();
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(
            "CALL chat_stats.sp_get_user_permissions(?);")) {
      stmt.setString(1, user);
      ResultSet resultSet = stmt.executeQuery();
      if (!resultSet.next()) {
        return permissionMap;
      }
      String permissions = resultSet.getString("permissions");
      permissionMap = new ObjectMapper().readValue(permissions,
          TypeFactory.defaultInstance()
              .constructMapType(HashMap.class, String.class, Boolean.class));
    } catch (SQLException e) {
      log.error("SQLException: {}, VendorError: {}", e.getMessage(), e.getErrorCode());
    } catch (JsonProcessingException e) {
      log.error("Error processing json to string: {}", e.getMessage());
    }
    return permissionMap;
  }

}
