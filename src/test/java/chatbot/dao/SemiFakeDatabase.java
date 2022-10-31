package chatbot.dao;


import chatbot.message.LoggableMessage;
import chatbot.message.TimeoutMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

/**
 * Uses the regular database connection for read operations, but not the writing ones. Messages that
 * were added using write commands can be extracted with get methods.
 */
@Log4j2
public class SemiFakeDatabase implements DatabaseHandler {

  DatabaseHandler realDatabaseHandler = new SQLSolrHandler();

  @Override
  public int getMessageCount(String username) {
    return realDatabaseHandler.getMessageCount(username);
  }

  @Override
  public List<String> getModList() {
    return realDatabaseHandler.getModList();
  }

  @Override
  public List<String> getAltsList() {
    return realDatabaseHandler.getAltsList();
  }

  @Getter
  List<TimeoutMessage> addedTimeouts = new ArrayList<>();

  @Override
  public void addTimeout(TimeoutMessage timeout) {
    log.info(timeout);
    addedTimeouts.add(timeout);
  }

  @Override
  public int getTimeoutAmount(String username) {
    return realDatabaseHandler.getTimeoutAmount(username);
  }

  @Override
  public String getTopTimeouts() {
    return realDatabaseHandler.getTopTimeouts();
  }

  @Getter
  List<TimeoutMessage> addedNamListTimeouts = new ArrayList<>();

  @Override
  public void addNamListTimeout(TimeoutMessage timeout) {
    log.info(timeout);
    addedNamListTimeouts.add(timeout);
  }

  @Getter
  List<LoggableMessage> addedWhispers = new ArrayList<>();

  @Override
  public void recordWhisper(LoggableMessage message) {
    log.info(message);
    addedWhispers.add(message);
  }

  @Getter
  List<LoggableMessage> addedMessages = new ArrayList<>();

  @Override
  public void recordMessage(LoggableMessage message) {
    log.info(message);
    addedWhispers.add(message);
  }

  @Override
  public String firstOccurrence(String msg) {
    return realDatabaseHandler.firstOccurrence(msg);
  }

  @Override
  public String firstMessage(String username) {
    return realDatabaseHandler.firstMessage(username);
  }

  @Override
  public String lastMessage(String username) {
    return realDatabaseHandler.lastMessage(username);
  }

  @Override
  public String randomSearch(String username, String msg) {
    return realDatabaseHandler.randomSearch(username, msg);
  }

  @Override
  public String randomQuote(String username, String year) {
    return realDatabaseHandler.randomQuote(username, year);
  }

  @Override
  public String getLogs(String username, int count) {
    return realDatabaseHandler.getLogs(username, count);
  }

  @Override
  public long search(String msg) {
    return realDatabaseHandler.search(msg);
  }

  @Override
  public long searchUser(String username, String msg) {
    return realDatabaseHandler.search(msg);
  }

  @Override
  public Map<String, String> getBlacklist() {
    return realDatabaseHandler.getBlacklist();
  }

  @Override
  public List<String> getDisabledList() {
    return realDatabaseHandler.getDisabledList();
  }

  @Getter
  List<String> addedDisableds;

  @Override
  public void addDisabled(String from, String username) {
    log.info("addDisabled {} {}", from, username);
    addedDisableds.add("%s %s".formatted(from, username));
  }

  @Getter
  List<String> removedDisableds;

  @Override
  public void removeDisabled(String username) {
    log.info("removeDisabled {}", username);
    removedDisableds.add(username);
  }

  @Override
  public List<String> getAlternateNames(String username) {
    return realDatabaseHandler.getAlternateNames(username);
  }

  @Getter
  List<String> addedAlts;

  @Override
  public boolean addAlt(String main, String alt) {
    log.info("addAlt {} {}", main, alt);
    addedAlts.add("%s %s".formatted(main, alt));
    return true;
  }

  @Getter
  List<String> commandPermissionUsers;

  @Override
  public void setCommandPermissionUser(String user, String command, boolean enable) {
    log.info("setCommandPermissionUser {} {} {}", user, command, enable);
    commandPermissionUsers.add("%s %s %b".formatted(user, command, enable));

  }

  @Override
  public HashMap<String, Boolean> getPersonalPermissions(String user) {
    return realDatabaseHandler.getPersonalPermissions(user);
  }
}
