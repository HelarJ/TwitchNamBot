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
import chatbot.singleton.SharedStateSingleton;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import lombok.extern.log4j.Log4j2;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Log4j2
public class ProgramThread implements Runnable {
    private final ListenerService listenerService;
    private final SenderService senderService;
    private final CommandHandlerService commandHandlerService;
    private final MessageLoggerService messageLoggerService;
    private final TimeoutLoggerService timeoutLoggerService;
    private final OnlineCheckerService onlineCheckerService;
    private final CountDownLatch done = new CountDownLatch(1);
    private ServiceManager serviceManager;
    private final SharedStateSingleton state = SharedStateSingleton.getInstance();

    public ProgramThread() throws IOException {
        state.initializeQueues();
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

            state.poisonQueues();
            serviceManager.stopAsync();
            serviceManager.awaitStopped(60, TimeUnit.SECONDS);

        } catch (IOException | InterruptedException e) {
            log.error("Connection error: {}", e.getMessage());
            shutdown();
        } catch (TimeoutException e) {
            log.fatal("Closing services timed out: {} . Current states: {}", e.getMessage(), serviceManager.servicesByState());

            //Having services still running might lead to issues, so we close the program completely.
            state.stop();
        }

        log.info("Mainthread thread ended.");
    }

    private void addListenersToManager(ServiceManager serviceManager) {
        serviceManager.addListener(new ServiceManager.Listener() {
            @Override
            public void failure(@Nonnull Service service) {
                log.fatal("{} failed. reason {}", service, service.failureCause());
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
    }
}
