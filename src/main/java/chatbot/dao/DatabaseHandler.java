package chatbot.dao;

import chatbot.dataclass.Message;
import chatbot.dataclass.Timeout;

import java.util.List;
import java.util.Map;

public interface DatabaseHandler {
    void addUsername(Timeout timeout);

    void addTimeout(Timeout timeout);

    int getTimeoutAmount(String username);

    void addNamListTimeout(Timeout timeout);

    void recordWhisper(Message message);

    void recordMessage(Message message);

    String firstOccurrence(String from, String msg);

    String randomSearch(String from, String username, String msg);

    Map<String, String> getBlacklist();

    List<String> getDisabledList();

    List<String> getAlternateNames(String username);

    boolean addAlt(String main, String alt);
}
