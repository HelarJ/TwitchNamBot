package chatbot.service;

import chatbot.ConsoleMain;
import chatbot.dao.api.ApiHandler;
import chatbot.singleton.SharedState;
import com.google.common.util.concurrent.AbstractScheduledService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class OnlineCheckerService extends AbstractScheduledService {

    private final static Logger log = LogManager.getLogger(OnlineCheckerService.class);

    private final ApiHandler apiHandler;
    private final SharedState state = SharedState.getInstance();

    public OnlineCheckerService(ApiHandler apiHandler) {
        this.apiHandler = apiHandler;
    }

    @Override
    protected void runOneIteration() {
        if (apiHandler.oauth == null) {
            apiHandler.setOauth();
        }
        apiHandler.checkOnline();
        checkPing();
    }

    @Override
    protected void startUp() {

        log.debug("{} started.", OnlineCheckerService.class);
    }

    @Override
    protected void shutDown() {
        log.debug("{} stopped.", OnlineCheckerService.class);
    }

    @Override
    @Nonnull
    protected Scheduler scheduler() {
        return Scheduler.newFixedRateSchedule(0, 5, TimeUnit.SECONDS);
    }

    private void checkPing() {
        Instant lastPing = state.lastPing.get();
        Instant lastMessage = state.lastMessageTime.get();

        if (lastPing != Instant.EPOCH
                && Instant.now().minus(6, ChronoUnit.MINUTES).isAfter(lastPing)
                && Instant.now().minus(3, ChronoUnit.MINUTES).isAfter(lastMessage)
        ) {
            log.error("6 minutes since Last ping: {}, last message: {}", lastPing, lastMessage);
            ConsoleMain.reconnect();
        }
    }

}
