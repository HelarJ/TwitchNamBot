package chatbot.dao;

import chatbot.message.LoggableMessage;
import chatbot.message.TimeoutMessage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface DatabaseHandler {

  int getMessageCount(String username);

  List<String> getModList();

  List<String> getAltsList();

  void addTimeout(TimeoutMessage timeout);

  int getTimeoutAmount(String username);

  String getTopTimeouts();

  void addNamListTimeout(TimeoutMessage timeout);

  void recordWhisper(LoggableMessage message);

  void recordMessage(LoggableMessage message);

  String firstOccurrence(String msg);

  String firstMessage(String username);

  String lastMessage(String username);

  String randomSearch(String username, String msg);

  String randomQuote(String username, String year);

  String getLogs(String username, int count);

  long search(String msg);

  long searchUser(String username, String msg);

  Map<String, String> getBlacklist();

  List<String> getDisabledList();

  void addDisabled(String from, String username);

  void removeDisabled(String username);

  List<String> getAlternateNames(String username);

  boolean addAlt(String main, String alt);

  void setCommandPermissionUser(String user, String command, boolean enable);

  HashMap<String, Boolean> getPersonalPermissions(String user);

}
