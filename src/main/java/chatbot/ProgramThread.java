package chatbot;

import chatbot.connector.MessageConnector;
import chatbot.dao.api.ApiHandler;
import chatbot.dao.db.DatabaseHandler;
import chatbot.dao.db.Maria;
import chatbot.dao.db.MultiDatabaseHandler;
import chatbot.dao.db.Postgres;
import chatbot.dao.db.SQLSolrHandler;
import chatbot.dao.db.Solr;
import chatbot.service.CommandHandlerService;
import chatbot.service.ListenerService;
import chatbot.service.MessageLoggerService;
import chatbot.service.OnlineCheckerService;
import chatbot.service.SenderService;
import chatbot.service.TimeoutLoggerService;
import chatbot.singleton.SharedState;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nonnull;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ProgramThread implements Runnable {

    private final static Logger log = LogManager.getLogger(ProgramThread.class);

    private final ListenerService listenerService;
    private final SenderService senderService;
    private final CommandHandlerService commandHandlerService;
    private final MessageLoggerService messageLoggerService;
    private final TimeoutLoggerService timeoutLoggerService;
    private final OnlineCheckerService onlineCheckerService;
    private final CountDownLatch done = new CountDownLatch(1);
    private final SharedState state = SharedState.getInstance();
    private final MessageConnector messageConnector;
    private final MultiDatabaseHandler dbLogger;

    private ServiceManager serviceManager;

    public ProgramThread(MessageConnector messageConnector) {
        this.messageConnector = messageConnector;
        state.clearQueues();

        DatabaseHandler databaseHandler = new SQLSolrHandler();

        this.dbLogger = new MultiDatabaseHandler(new Maria(), new Solr());
        this.senderService = new SenderService(messageConnector);
        this.commandHandlerService = new CommandHandlerService(databaseHandler);
        this.onlineCheckerService = new OnlineCheckerService(new ApiHandler());
        this.timeoutLoggerService = new TimeoutLoggerService(dbLogger);
        this.messageLoggerService = new MessageLoggerService(dbLogger);
        this.listenerService = new ListenerService(messageConnector);
    }

    @Override
    public void run() {
        try {
            state.lastPing.set(Instant.EPOCH);
            ArrayList<Service> services = Lists.newArrayList(senderService, listenerService,
                    commandHandlerService, messageLoggerService, timeoutLoggerService,
                    onlineCheckerService);
            this.serviceManager = new ServiceManager(services);
            addListenersToManager(serviceManager);
            serviceManager.startAsync();
            serviceManager.awaitHealthy(10, TimeUnit.SECONDS);
            senderService.connect();
            done.await();

            serviceManager.stopAsync();
            serviceManager.awaitStopped(60, TimeUnit.SECONDS);

        } catch (IOException | IllegalStateException | InterruptedException e) {
            log.error("Connection error: {}", e.getMessage());
            shutdown();
        } catch (TimeoutException e) {
            log.error("Closing services timed out: {} . Current states: {}", e.getMessage(),
                    serviceManager.servicesByState());
        }
        log.info("Mainthread thread ended.");
    }

    private void addListenersToManager(ServiceManager serviceManager) {
        serviceManager.addListener(new ServiceManager.Listener() {
            @Override
            public void failure(@Nonnull Service service) {
                log.error("{} failed. reason {}", service, service.failureCause());
                shutdown();
            }

            @Override
            public void healthy() {
                log.info("Started services: {}", serviceManager.startupDurations());
            }

            @Override
            public void stopped() {
                log.info("Stopped services");
            }

        }, MoreExecutors.directExecutor());
    }

    public void shutdown() {
        done.countDown();
        state.poisonQueues();
        dbLogger.destroy();
        messageConnector.close();
    }
}
