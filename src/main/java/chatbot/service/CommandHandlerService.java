package chatbot.service;

import chatbot.ConsoleMain;
import chatbot.dao.api.ApiHandler;
import chatbot.dao.db.DatabaseHandler;
import chatbot.enums.Command;
import chatbot.enums.Response;
import chatbot.message.CommandMessage;
import chatbot.message.Message;
import chatbot.message.PoisonMessage;
import chatbot.message.SimpleMessage;
import chatbot.singleton.ConfigSingleton;
import chatbot.singleton.SharedStateSingleton;
import chatbot.utils.Utils;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class CommandHandlerService extends AbstractExecutionThreadService {

  private final ConfigSingleton config = ConfigSingleton.getInstance();
  private final HashMap<String, Instant> banned = new HashMap<>();
  private final HashMap<String, Instant> superbanned = new HashMap<>();
  private final String website = config.getBotWebsite();
  private final String botName = config.getTwitchUsername();
  private final String admin = config.getBotAdmin();
  private final String channel = config.getChannelToJoin();
  private final List<Instant> previousMessageTimes = new ArrayList<>();
  private final DatabaseHandler databaseHandler;
  private final ApiHandler apiHandler;
  private final SharedStateSingleton state = SharedStateSingleton.getInstance();
  private Instant lastCommandTime = Instant.now().minus(30, ChronoUnit.SECONDS);
  private HashSet<String> godUsers;
  private HashSet<String> mods;
  private String previousMessage = "";

  public CommandHandlerService(DatabaseHandler databaseHandler, ApiHandler apiHandler) {
    this.databaseHandler = databaseHandler;
    this.apiHandler = apiHandler;
    refreshLists(new SimpleMessage("Startup", ""));
  }

  @Override
  protected void shutDown() {
    log.debug("{} stopped.", CommandHandlerService.class);
  }

  @Override
  protected void startUp() {
    log.debug("{} started.", CommandHandlerService.class);
  }

  @Override
  public void run() throws InterruptedException {
    while (state.isBotStillRunning()) {
      Message message = state.commandHandlerBlockingQueue.take();
      if (message instanceof PoisonMessage) {
        log.debug(CommandHandlerService.class + " poisoned.");
        break;
      }
      if (!(message instanceof CommandMessage commandMessage)) {
        log.error("Unexcpected message type in commandqueue {}", message);
        continue;
      }
      handleCommand(commandMessage);
    }
  }

  private void handleCommand(CommandMessage message) {
    Command command = message.getCommand();
    if (command == null) {
      return;
    }

    log.info(String.format("%s used %s with %s.", message.getSender(), command,
        message.getStringMessage()));

    if (!isAllowed(message)) {
      log.info(message.getSender() + " not allowed to use command " + command);
      return;
    }

    switch (command) {
      case NAMMERS -> topNammers(message);
      case NAMPING -> ping(message);
      case NAMBAN -> ban(message);
      case NAMES -> names(message);
      case NAMREFRESH -> refreshLists(message);
      case NAMCOMMANDS -> namCommands(message);
      case NAMCHOOSE -> choose(message);
      case NAM -> userNam(message);
      case LASTMESSAGE -> lastMessage(message);
      case FIRSTMESSAGE -> firstMessage(message);
      case LOG, LOGS -> getLogs(message);
      case RQ -> randomQuote(message);
      case RS -> randomSearch(message);
      case ADDDISABLED -> addDisabled(message);
      case REMDISABLED -> removeDisabled(message);
      case FS -> firstOccurrence(message);
      case SEARCH -> search(message);
      case SEARCHUSER -> searchUser(message);
      case ADDALT -> addAlt(message);
      case SC -> setCommandPermissionUser(message);
    }
    lastCommandTime = Instant.now();
  }

  private void setCommandPermissionUser(CommandMessage message) {
    String commandName = Utils.getArg(message.getMessageWithoutUsername().toLowerCase(), 0);
    String bool = Utils.getArg(message.getMessageWithoutUsername(), 1);
    if (commandName == null || bool == null) {
      return;
    }
    databaseHandler.setCommandPermissionUser(message.getUsername(), commandName,
        Boolean.parseBoolean(bool));

    state.sendingBlockingQueue.add(message.setResponse(
        "@%s, set command %s permissions for %s to %s.".formatted(message.getSender(), commandName,
            message.getUsername(),
            Boolean.parseBoolean(bool))));
  }

  public void refreshLists(Message message) {
    if (message.getSender().equals("Startup") || godUsers.stream()
        .anyMatch(message.getSender()::equalsIgnoreCase)) {
      initializeMods();
      initializeDisabled();
      initializeAlts();
      initializeBlacklist();
      if (!message.getSender().equals("Startup")) {
        state.sendingBlockingQueue.add(
            new SimpleMessage(message.getSender(), "Lists refreshed HACKERMANS"));
      }
    }
  }

  private void namCommands(CommandMessage message) {
    state.sendingBlockingQueue.add(message.setResponse(
        "@" + message.getSender() + ", commands for this bot: " + website + "/commands"));

  }

  private void ban(CommandMessage message) {
    superbanned.put(message.getUsername(), Instant.now());
    state.sendingBlockingQueue.add(
        message.setResponse("Banned " + message.getUsername() + " from using the bot for 1h."));
  }

  private void names(CommandMessage message) {
    StringBuilder names = new StringBuilder("@");
    names.append(message.getSender()).append(", ").append(message.getUsername())
        .append("'s other names are: ");
    var nameList = databaseHandler.getAlternateNames(message.getUsername());
    for (String name : nameList) {
      names.append(name).append(", ");
    }
    if (nameList.size() >= 1) {
      names.setLength(names.length() - 2);
      state.sendingBlockingQueue.add(message.setResponse(names.toString()));
    } else {
      state.sendingBlockingQueue.add(
          message.setResponse(
              "@" + message.getSender() + ", no alternate names found in logs PEEPERS"));
    }
  }

  private void choose(CommandMessage message) {
    String[] choices = message.getMessageWithoutCommand().split(" ");
    if (choices.length == 0) {
      return;
    }
    int choice = ThreadLocalRandom.current().nextInt(0, choices.length);
    state.sendingBlockingQueue.add(
        message.setResponse(
            String.format("@%s, I choose %s", message.getSender(), choices[choice])));
  }

  private void ping(CommandMessage message) {
    state.sendingBlockingQueue.add(message.setResponse(String.format(
        "NamBot online for %s | %d messages sent | %d messages logged | %d timeouts logged, of which %d were permabans.",
        (Utils.convertTime(
            (int) (Instant.now().minus(ConsoleMain.getStartTime().toEpochMilli(), ChronoUnit.MILLIS)
                .toEpochMilli() / 1000))),
        state.getSentMessageCount(), state.getMessageCount(), state.getTimeoutCount(),
        state.getPermabanCount())));

  }

  private void searchUser(CommandMessage message) {
    long count = databaseHandler.searchUser(message.getUsername(),
        message.getMessageWithoutUsername());
    if (count == 0) {
      state.sendingBlockingQueue.add(
          message.setResponse("@%s, %s".formatted(message.getSender(), Response.NO_MESSAGES)));
    } else {
      state.sendingBlockingQueue.add(message.setResponse(
          "@%s, %s has used %s in %d messages".formatted(message.getSender(), message.getUsername(),
              Utils.getWordList(message.getMessageWithoutUsername()), count)));
    }
  }

  private void search(CommandMessage message) {
    long count = databaseHandler.search(message.getMessageWithoutCommand());
    if (count == 0) {
      state.sendingBlockingQueue.add(
          message.setResponse("@%s, %s".formatted(message.getSender(), Response.NO_MESSAGES)));
    } else {
      state.sendingBlockingQueue.add(message.setResponse(
          "@" + message.getSender() + " found " + Utils.getWordList(
              message.getMessageWithoutCommand()) + " in " + count
              + " rows."));
    }
  }

  private void firstOccurrence(CommandMessage message) {
    state.sendingBlockingQueue.add(message.setResponse(
        "@%s, %s".formatted(message.getSender(),
            databaseHandler.firstOccurrence(message.getMessageWithoutCommand()))));
  }

  private void randomSearch(CommandMessage message) {

    String result = databaseHandler.randomSearch(message.getUsername(),
        message.getMessageWithoutUsername());
    if (!result.startsWith("[")) {
      result = "%s, %s".formatted(message.getSender(), result);
    }
    state.sendingBlockingQueue.add(message.setResponse(result));
  }

  private void randomQuote(CommandMessage message) {
    if (hasNoMessages(message)) {
      return;
    }
    String result = databaseHandler.randomQuote(message.getUsername(), message.getYear());
    if (!result.startsWith("[")) {
      result = "%s, %s".formatted(message.getSender(), result);
    }
    state.sendingBlockingQueue.add(message.setResponse(result));
  }

  private boolean isBot(String username) {
    if (username.equalsIgnoreCase(botName)) {
      state.sendingBlockingQueue.add(new SimpleMessage("isBot", "PepeSpin"));
      return true;
    }
    return false;
  }

  private void userNam(CommandMessage message) {
    int timeout = databaseHandler.getTimeoutAmount(message.getUsername());
    if (timeout > 0) {
      String response;
      if (message.getSender().equalsIgnoreCase(message.getUsername())) {
        response = String.format("@%s, you have spent %s in the shadow realm.",
            message.getUsername(),
            Utils.convertTime(timeout));
      } else {
        response = String.format("%s has spent %s in the shadow realm.", message.getUsername(),
            Utils.convertTime(timeout));
      }
      state.sendingBlockingQueue.add(message.setResponse(response));

    }
    lastCommandTime = Instant.now();
  }

  private void firstMessage(CommandMessage message) {
    if (hasNoMessages(message)) {
      return;
    }
    state.sendingBlockingQueue.add(
        message.setResponse("%s, %s".formatted(message.getSender(),
            databaseHandler.firstMessage(message.getUsername()))));
  }

  private void lastMessage(CommandMessage message) {
    if (hasNoMessages(message)) {
      return;
    }
    state.sendingBlockingQueue.add(
        message.setResponse("@%s, %s".formatted(message.getSender(),
            databaseHandler.lastMessage(message.getUsername()))));
  }

  private boolean hasNoMessages(CommandMessage message) {
    int count = getCount(message.getUsername());
    if (count == 0) {
      log.info("Did not find any messages for user {}", message.getUsername());
      state.sendingBlockingQueue.add(
          message.setResponse("@%s, %s".formatted(message.getSender(), Response.NO_MESSAGES)));
      return true;
    }
    return false;
  }

  private void topNammers(CommandMessage message) {
    String topTimeoutList = databaseHandler.getTopTimeouts();
    if (topTimeoutList == null) {
      return;
    }
    state.sendingBlockingQueue.add(message.setResponse(topTimeoutList));
  }

  private int getCount(String username) {
    return databaseHandler.getMessageCount(username);
  }

  private void initializeMods() {
    this.mods = new HashSet<>();
    this.godUsers = new HashSet<>();
    mods.addAll(databaseHandler.getModList());
    godUsers.add(admin);
    godUsers.add("Autoban");
    godUsers.add(channel.replace("#", ""));
    log.info("Initialized mods list {} | god users {}.", mods, godUsers);
  }

  private void addAlt(CommandMessage message) {
    String main = Utils.getArg(message.getMessageWithoutCommand(), 0);
    if (main == null) {
      return;
    }
    main = main.toLowerCase();
    String alt = Utils.getArg(message.getMessageWithoutCommand(), 1);
    if (alt == null) {
      return;
    }
    alt = alt.toLowerCase();
    if (state.alts.containsKey(main)) {
      if (state.alts.get(main).contains(alt)) {
        return;
      }
    }
    log.info("{} adding {} to {}'s alt list", message.getSender(), alt, main);
    if (!databaseHandler.addAlt(main, alt)) {
      log.error("Adding alt was unsuccessful: {} - {}.", main, alt);
      state.sendingBlockingQueue.add(message.setResponse(Response.INTERNAL_ERROR));
      return;
    }
    state.mains.put(alt, main);
    state.mains.putIfAbsent(main, main);
    state.alts.putIfAbsent(main, new ArrayList<>());
    state.alts.get(main).add(alt);
    state.sendingBlockingQueue.add(
        message.setResponse(
            "@" + message.getSender() + ", added " + alt + " as " + main + "'s alt account."));
  }

  void addDisabled(CommandMessage message) {
    if (state.disabledUsers.contains(message.getUsername())) {
      return;
    }

    state.disabledUsers.add(message.getUsername());
    databaseHandler.addDisabled(message.getSender(), message.getUsername());

    if (!message.getSender().equals("Autoban") && (
        mods.stream().anyMatch(message.getSender()::equalsIgnoreCase) || (
            lastCommandTime.plus(10, ChronoUnit.SECONDS).isBefore(Instant.now())
                && state.online.get()))) {
      state.sendingBlockingQueue.add(
          message.setResponse(
              "@" + message.getSender() + ", added " + message.getUsername() + " to ignore list."));
    }
    log.info("{} added {} to disabled list", message.getSender(), message.getUsername());
  }

  private void removeDisabled(CommandMessage message) {
    if (!state.disabledUsers.contains(message.getUsername())) {
      return;
    }
    state.disabledUsers.remove(message.getUsername());
    if (mods.contains(message.getSender().toLowerCase()) || lastCommandTime.plus(10,
            ChronoUnit.SECONDS)
        .isBefore(Instant.now())) {
      state.sendingBlockingQueue.add(
          message.setResponse("@" + message.getSender() + ", removed " + message.getUsername()
              + " from ignore list."));
    }
    databaseHandler.removeDisabled(message.getUsername());
    log.info("{} removed {} from disabled list", message.getSender(), message.getUsername());

  }


  private void getLogs(CommandMessage message) {
    if (!message.getUsername().equalsIgnoreCase("all") && hasNoMessages(message)) {
      return;
    }

    if (apiHandler.isLogsApiOnline()) {
      final String logSite = website + "/logs/";
      String response = String.format("@%s logs for %s: %s%s", message.getSender(),
          message.getUsername(), logSite,
          message.getUsername());

      state.sendingBlockingQueue.add(message.setResponse(response));
    } else {
      log.error("NamLogAPI is down.");
      state.sendingBlockingQueue.add(message.setResponse(Response.INTERNAL_ERROR));
    }
  }


  private void initializeBlacklist() {
    ArrayList<String> blacklist = new ArrayList<>();
    ArrayList<String> textBlacklist = new ArrayList<>();
    StringBuilder replacelistSb = new StringBuilder();
    Map<String, String> map = databaseHandler.getBlacklist();
    for (Map.Entry<String, String> entry : map.entrySet()) {
      switch (entry.getValue()) {
        case "word" -> blacklist.add(entry.getKey());
        case "text" -> textBlacklist.add(entry.getKey());
        case "replace" -> replacelistSb.append("|").append(entry.getKey());
      }
    }
    replacelistSb.replace(0, 1, "");
    state.setBlacklist(blacklist, textBlacklist, replacelistSb.toString());
    log.info("Blacklist initialized. {} total words in blacklist.", map.size());
  }

  private void initializeDisabled() {
    state.setDisabledUsers(databaseHandler.getDisabledList());
    log.info("Disabled list initialized. {} disabled users.", state.disabledUsers.size());
  }

  private void initializeAlts() {
    state.mains = new HashMap<>();
    state.alts = new HashMap<>();

    List<String> mainsAltsCsv = databaseHandler.getAltsList();

    for (String csv : mainsAltsCsv) {
      String[] values = csv.split(",");
      String main = values[0];
      String alt = values[1];

      state.mains.putIfAbsent(alt, main);
      state.mains.putIfAbsent(main, main);
      state.alts.putIfAbsent(main, new ArrayList<>());
      state.alts.get(main).add(alt);
    }
    log.info("Initialized alts list. {} users with alts.", state.alts.size());
  }

  private boolean checkOneManSpam(String from) {
    if (!previousMessage.equals(from)) {
      previousMessage = from;
      previousMessageTimes.clear();
    }
    previousMessageTimes.add(Instant.now());
    previousMessageTimes.removeIf(
        instant -> instant.plus(300, ChronoUnit.SECONDS).isAfter(Instant.now()));
    return previousMessageTimes.size() >= 5;
  }

  public boolean isAllowed(CommandMessage message) {
    String from = message.getSender();
    String username = message.getUsername();
    Command command = message.getCommand();

    //admins are not affected by any rules except optout rules.
    if (godUsers.stream().anyMatch(from::equalsIgnoreCase) && !command.isOptedOut(username,
        state.disabledUsers)) {
      return true;
    }

    if (isBanned(from)) {
      return false;
    }

    //online check
    if (state.online.get() && !command.isOnlineAllowed()) {
      log.info("Attempted to use " + command + " while stream is online");
      return false;
    }

    //10 second cooldown
    if (lastCommandTime.plus(10, ChronoUnit.SECONDS).isAfter(Instant.now()) && godUsers.stream()
        .noneMatch(from::equalsIgnoreCase)
        //These commands have their own logic for cooldown.
        && command != Command.ADDDISABLED && command != Command.REMDISABLED) {
      return false;
    }

    //new spammer check
    if (checkOneManSpam(from)) {
      banned.put(from, Instant.now());
      state.sendingBlockingQueue.add(
          message.setResponse("@" + from
              + ", stop one man spamming. Banned from using commands for 10 minutes peepoD"));
      return false;
    }

    if (isBot(username)) {
      return false;
    }

    //wildcard in username not allowed
    if (username.contains("*") || username.contains("?") || username.contains("~")
        || username.contains("{")
        || username.contains("[")) {
      return false;
    }

    if (command.isOptedOut(username, state.disabledUsers)) {
      state.sendingBlockingQueue.add(
          message.setResponse("@" + from + ", that user has been removed from the " + command
              + " command. Type !adddisabled to remove yourself or !remdisabled to re-enable commands."));
      lastCommandTime = Instant.now();
      return false;
    }

    //check database for user specific command permissions
    HashMap<String, Boolean> userPermissionMap = databaseHandler.getPersonalPermissions(from);
    Boolean specified = command.isUserCommandSpecified(userPermissionMap);
    if (specified != null) {
      log.info("User {} command specific permission was {}.", from, specified);
      return specified;
    }

    return command.isSelfAllowed(from, username) || command.isOthersAllowed()
        || command.isModsAllowed(from, mods);
  }

  private boolean isBanned(String from) {
    //previous spammer check
    if (banned.containsKey(from.toLowerCase())) {
      if (banned.get(from).plus(600, ChronoUnit.SECONDS).isAfter(Instant.now())) {
        log.info("Banned user " + from + " attempted to use a command.");
        return true;
      } else {
        banned.remove(from);
      }
    }

    //manually banned check
    if (superbanned.containsKey(from.toLowerCase())) {
      if (superbanned.get(from).plus(3600, ChronoUnit.SECONDS).isAfter(Instant.now())) {
        log.info("superbanned user " + from + " attempted to use a command.");
        return true;
      } else {
        superbanned.remove(from);
      }
    }
    return false;
  }
}
