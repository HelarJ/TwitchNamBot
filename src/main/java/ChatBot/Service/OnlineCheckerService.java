package ChatBot.Service;

import ChatBot.StaticUtils.Running;
import ChatBot.dao.ApiHandler;
import com.google.common.util.concurrent.AbstractExecutionThreadService;

import java.util.logging.Logger;

public class OnlineCheckerService extends AbstractExecutionThreadService {
    private final Logger logger = Logger.getLogger(OnlineCheckerService.class.toString());
    private final ApiHandler apiHandler = new ApiHandler();

    public boolean running = true;

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
        while (Running.isBotStillRunning() && running) {
            apiHandler.checkOnline(this);
        }
    }
}
