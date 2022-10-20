package chatbot.service;

import chatbot.dao.ApiHandler;
import chatbot.singleton.SharedStateSingleton;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class OnlineCheckerService extends AbstractExecutionThreadService {
    private final ApiHandler apiHandler;
    public boolean running = true;
    private final SharedStateSingleton state = SharedStateSingleton.getInstance();

    public OnlineCheckerService(ApiHandler apiHandler) {
        this.apiHandler = apiHandler;
    }

    @Override
    protected void triggerShutdown() {
        running = false;
    }

    @Override
    protected void startUp() {
        log.info("{} started.", OnlineCheckerService.class);
    }

    @Override
    protected void shutDown() {
        log.info("{} stopped.", OnlineCheckerService.class);
    }

    @Override
    public void run() {
        if (apiHandler.oauth == null) {
            apiHandler.oauth = apiHandler.getOauth();
        }

        while (state.isBotStillRunning() && running) {
            apiHandler.checkOnline(this);
        }
    }
}
