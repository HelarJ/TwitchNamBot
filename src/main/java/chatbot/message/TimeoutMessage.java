package chatbot.message;

import lombok.extern.log4j.Log4j2;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Log4j2
public class TimeoutMessage implements Message {
    private final String username;
    private Instant timeout;
    private int length;
    private final String userid;

    public TimeoutMessage(String username, String userid, int length) {
        this.username = username;
        this.timeout = Instant.now();
        this.length = length;
        this.userid = userid;
    }

    public boolean hasExpired() {
        return timeout.plus(length, ChronoUnit.SECONDS).isBefore(Instant.now());
    }

    /**
     * For tracked timeouts that should currently be active.
     * If the timeout was reset it means that the user probably got untimeouted or timed out with a lower time.
     *
     * @param length new timeout length.
     */
    public void resetTimeout(int length) {
        if (length == this.length) {
            return;
        }
        int prevlength = this.length;
        this.length = length;
        log.info("Set {}'s {}s timeout to {}s.", username, prevlength, length);
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

    @Override
    public String toString() {
        return "Timeout{" +
                "username='" + username + '\'' +
                ", timeout=" + timeout +
                ", length=" + length +
                '}';
    }

    @Override
    public String getSender() {
        return username;
    }

    @Override
    public String getStringMessage() {
        return toString();
    }
}
