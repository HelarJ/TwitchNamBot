package ChatBot.Dataclass;

import ChatBot.StaticUtils.Running;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class Timeout {
    private final String username;
    private Instant timeout;
    private int length;

    public Timeout(String username, int length) {
        this.username = username;
        this.timeout = Instant.now();
        this.length = length;
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

    @Override
    public String toString() {
        return "Timeout{" +
                "username='" + username + '\'' +
                ", timeout=" + timeout +
                ", length=" + length +
                '}';
    }
}
