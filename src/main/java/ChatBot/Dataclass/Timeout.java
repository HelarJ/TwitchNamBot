package ChatBot.Dataclass;

import ChatBot.StaticUtils.Running;
import ChatBot.enums.MessageType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class Timeout {
    private String username;
    private Instant timeout;
    private int length;
    private String userid;
    private final MessageType type;

    public Timeout(String username, String userid, int length) {
        this.username = username;
        this.timeout = Instant.now();
        this.length = length;
        this.userid = userid;
        type = MessageType.TIMEOUT;
    }

    public boolean hasExpired() {
        return timeout.plus(length, ChronoUnit.SECONDS).isBefore(Instant.now());
    }

    /**
     * For duplicate timeouts.
     *
     * @param length new timeout length.
     */
    public void resetTimeout(int length) {
        int prevlength = this.length;
        this.length = length;
        Running.getLogger().info(String.format("Set %s's %d timeout to %ds.", username, prevlength, length));
        this.timeout = Instant.now();
    }

    public String getUsername() {
        return username;
    }

    public int getLength() {
        return length;
    }

    public String getUserid() {
        return userid;
    }

    public Timeout(MessageType type) {
        this.type = type;
    }

    public boolean isPoison() {
        return type == MessageType.POISON;
    }

    @Override
    public String toString() {
        return "Timeout{" +
                "username='" + username + '\'' +
                ", timeout=" + timeout +
                ", length=" + length +
                '}';
    }
}
