package chatbot.service;

import chatbot.dao.ApiHandler;
import chatbot.singleton.SharedStateSingleton;
import com.google.common.util.concurrent.AbstractExecutionThreadService;

import java.util.logging.Logger;

public class OnlineCheckerService extends AbstractExecutionThreadService {
    private final Logger logger = Logger.getLogger(OnlineCheckerService.class.toString());
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
        logger.info(OnlineCheckerService.class + " started.");
    }

    @Override
    protected void shutDown() {
        logger.info(OnlineCheckerService.class + " stopped.");
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
