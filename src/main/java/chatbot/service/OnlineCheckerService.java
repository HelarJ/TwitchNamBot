package chatbot.service;

import chatbot.dao.api.ApiHandler;
import com.google.common.util.concurrent.AbstractScheduledService;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class OnlineCheckerService extends AbstractScheduledService {

  private final ApiHandler apiHandler;

  public OnlineCheckerService(ApiHandler apiHandler) {
    this.apiHandler = apiHandler;
  }

  @Override
  protected void runOneIteration() {
    if (apiHandler.oauth == null) {
      apiHandler.setOauth();
    }
    apiHandler.checkOnline();
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
}
