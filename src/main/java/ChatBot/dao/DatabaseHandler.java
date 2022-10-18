package ChatBot.dao;

import ChatBot.Dataclass.Message;
import ChatBot.Dataclass.Timeout;

public interface DatabaseHandler {
    void addUsername(Timeout timeout);

    void addTimeout(Timeout timeout);

    int getTimeoutAmount(String username);

    void addNamListTimeout(Timeout timeout);

    void recordWhisper(Message message);

    void recordMessage(Message message);

    String firstOccurrence(String from, String msg);
}
