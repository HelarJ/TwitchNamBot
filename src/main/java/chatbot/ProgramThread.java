package chatbot;

import chatbot.dao.ApiHandler;
import chatbot.dao.DatabaseHandler;
import chatbot.dao.SQLSolrHandler;
import chatbot.service.CommandHandlerService;
import chatbot.service.ListenerService;
import chatbot.service.MessageLoggerService;
import chatbot.service.OnlineCheckerService;
import chatbot.service.SenderService;
import chatbot.service.TimeoutLoggerService;
import chatbot.utils.Running;
import chatbot.utils.SharedQueues;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

public class ProgramThread implements Runnable {
    private static final Logger logger = Logger.getLogger(ProgramThread.class.toString());
    private final ListenerService listenerService;
    private final SenderService senderService;
    private final CommandHandlerService commandHandlerService;
    private final MessageLoggerService messageLoggerService;
    private final TimeoutLoggerService timeoutLoggerService;
    private final OnlineCheckerService onlineCheckerService;
    private final CountDownLatch done = new CountDownLatch(1);
    private ServiceManager serviceManager;

    public ProgramThread() throws IOException {
        ApiHandler apiHandler = new ApiHandler();
        DatabaseHandler databaseHandler = new SQLSolrHandler();
        Socket socket = new Socket("irc.chat.twitch.tv", 6667);
        this.senderService = new SenderService(socket);
        this.commandHandlerService = new CommandHandlerService(databaseHandler, apiHandler);
        this.onlineCheckerService = new OnlineCheckerService(apiHandler);
        this.timeoutLoggerService = new TimeoutLoggerService(databaseHandler);
        this.messageLoggerService = new MessageLoggerService(databaseHandler);
        this.listenerService = new ListenerService(socket, commandHandlerService);
    }

    @Override
    public void run() {
        try {
            ArrayList<Service> services = Lists.newArrayList(senderService, listenerService,
                    commandHandlerService, messageLoggerService, timeoutLoggerService, onlineCheckerService);
            this.serviceManager = new ServiceManager(services);
            addListenersToManager(serviceManager);
            serviceManager.startAsync();
            serviceManager.awaitHealthy();
            senderService.connect();

            done.await();

            SharedQueues.poisonQueues();
            serviceManager.stopAsync();
            serviceManager.awaitStopped(60, TimeUnit.SECONDS);

        } catch (IOException | InterruptedException e) {
            logger.severe("Connection error: " + e.getMessage());
            shutdown();
        } catch (TimeoutException e) {
            logger.severe("Closing services timed out. " + e.getMessage() +
                    ". Current states: " + serviceManager.servicesByState());

            //Having services still running might lead to issues, so we close the program completely.
            Running.stop();
        }

        logger.info("Mainthread thread ended.");
    }

    private void addListenersToManager(ServiceManager serviceManager) {
        serviceManager.addListener(new ServiceManager.Listener() {
            @Override
            public void failure(Service service) {
                logger.severe(service + " failed. reason " + service.failureCause());
            }

            @Override
            public void healthy() {
                logger.info("Started services");
                logger.info(String.valueOf(serviceManager.startupDurations()));
            }

            @Override
            public void stopped() {
                logger.info("Stopped services");
            }

        }, MoreExecutors.directExecutor());
    }

    public void shutdown() {
        done.countDown();
    }
}
