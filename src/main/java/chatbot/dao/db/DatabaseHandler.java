package chatbot.dao.db;

import java.util.*;

public interface DatabaseHandler {

    int getMessageCount(String username);

    boolean userHasAnyMessages(String username);

    List<String> getModList();

    List<String> getAltsList();

    int getTimeoutAmount(String username);

    Optional<String> getTopTimeouts();

    Optional<String> firstOccurrence(String msg);

    String firstMessage(String username);

    String lastMessage(String username);

    String lastSeen(String username);

    Optional<String> randomSearch(String username, String msg);

    Optional<String> randomQuote(String username, String year);

    long search(String msg);

    long searchUser(String username, String msg);

    Map<String, String> getBlacklist();

    Set<String> getDisabledList();

    void addDisabled(String from, String username);

    void removeDisabled(String username);

    Optional<List<String>> getAlternateNames(String username);

    boolean addAlt(String main, String alt);

    void setCommandPermissionUser(String user, String command, boolean enable);

    Map<String, Boolean> getPersonalPermissions(String user);

}
