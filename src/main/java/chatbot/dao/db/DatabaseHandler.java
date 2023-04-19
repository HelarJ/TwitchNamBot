package chatbot.dao.db;

import chatbot.message.LoggableMessage;
import chatbot.message.TimeoutMessage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface DatabaseHandler {

  int getMessageCount(String username);

  List<String> getModList();

  List<String> getAltsList();

  void addTimeout(TimeoutMessage timeout);

  int getTimeoutAmount(String username);

  Optional<String> getTopTimeouts();

  void addNamListTimeout(TimeoutMessage timeout);

  void recordWhisper(LoggableMessage message);

  void recordMessage(LoggableMessage message);

  Optional<String> firstOccurrence(String msg);

  String firstMessage(String username);

  String lastMessage(String username);

  Optional<String> randomSearch(String username, String msg);

  Optional<String> randomQuote(String username, String year);

  long search(String msg);

  long searchUser(String username, String msg);

  Map<String, String> getBlacklist();

  List<String> getDisabledList();

  void addDisabled(String from, String username);

  void removeDisabled(String username);

  Optional<List<String>> getAlternateNames(String username);

  boolean addAlt(String main, String alt);

  void setCommandPermissionUser(String user, String command, boolean enable);

  HashMap<String, Boolean> getPersonalPermissions(String user);

}
