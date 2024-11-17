package chatbot.service;

import chatbot.ConsoleMain;
import chatbot.dao.db.DatabaseHandler;
import chatbot.enums.Command;
import chatbot.enums.Response;
import chatbot.message.*;
import chatbot.singleton.Config;
import chatbot.singleton.SharedState;
import chatbot.utils.Utils;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class CommandHandlerService extends AbstractExecutionThreadService {

    private final static Logger log = LogManager.getLogger(CommandHandlerService.class);

    private final HashMap<String, Instant> banned = new HashMap<>();
    private final HashMap<String, Instant> manualBanned = new HashMap<>();
    private final String website = Config.getBotWebsite();
    private final String botName = Config.getTwitchUsername();
    private final String admin = Config.getBotAdmin();
    private final String channel = Config.getChannelToJoin();
    private final List<Instant> previousMessageTimes = new ArrayList<>();
    private final DatabaseHandler databaseHandler;
    private final SharedState state = SharedState.getInstance();
    private Instant lastCommandTime = Instant.now().minus(30, ChronoUnit.SECONDS);
    private HashSet<String> admins = new HashSet<>();
    private HashSet<String> mods = new HashSet<>();
    private String previousMessage = "";

    public CommandHandlerService(DatabaseHandler databaseHandler) {
        this.databaseHandler = databaseHandler;
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
                log.debug("{} poisoned.", CommandHandlerService.class);
                break;
            }
            if (!(message instanceof CommandMessage commandMessage)) {
                log.error("Unexpected message type in commandqueue {}", message);
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

        log.info("{} used {} with {}.", message.getSender(), command, message.getStringMessage());

        if (!isAllowed(message)) {
            log.info("{} not allowed to use command {}", message.getSender(), command);
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
            case LASTMESSAGE, LM -> lastMessage(message);
            case FIRSTMESSAGE, FM -> firstMessage(message);
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
            case LASTSEEN, LS -> lastSeen(message);
            case MCOUNT -> messageCount(message);
        }
        lastCommandTime = Instant.now();
    }

    private void setCommandPermissionUser(CommandMessage message) {
        String commandName = Utils.getArg(message.getMessageWithoutUsername().toLowerCase(), 0);
        String bool = Utils.getArg(message.getMessageWithoutUsername(), 1);
        if (commandName == null || bool == null) {
            return;
        }
        databaseHandler.setCommandPermissionUser(message.getUsername(), commandName, Boolean.parseBoolean(bool));

        state.sendingBlockingQueue.add(message.setResponse("@%s, set command %s permissions for %s to %s.".formatted(
                message.getSender(),
                commandName,
                message.getUsername(),
                Boolean.parseBoolean(bool))));
    }

    public void refreshLists(Message message) {
        if (message.getSender().equals("Startup") || admins.stream()
                .anyMatch(message.getSender()::equalsIgnoreCase))
        {
            initializeMods();
            initializeDisabled();
            initializeAlts();
            initializeBlacklist();
            if (!message.getSender().equals("Startup")) {
                state.sendingBlockingQueue.add(new SimpleMessage(message.getSender(), "Lists refreshed HACKERMANS"));
            }
        }
    }

    private void namCommands(CommandMessage message) {
        state.sendingBlockingQueue.add(message.setResponse("@%s, commands for this bot: %s/commands".formatted(message.getSender(), website)));

    }

    private void ban(CommandMessage message) {
        manualBanned.put(message.getUsername(), Instant.now());
        state.sendingBlockingQueue.add(message.setResponse("Banned %s from using the bot for 1h.".formatted(message.getUsername())));
    }

    private void names(CommandMessage message) {
        StringBuilder names = new StringBuilder("@");
        names.append(message.getSender()).append(", ").append(message.getUsername()).append("'s other names are: ");

        Optional<List<String>> optional = databaseHandler.getAlternateNames(message.getUsername());
        if (optional.isEmpty()) {
            state.sendingBlockingQueue.add(message.setResponse("@%s, multiple users have had that name PepeSpin".formatted(message.getSender())));
            return;
        }
        var nameList = optional.get();
        for (String name : nameList) {
            names.append(name).append(", ");
        }
        if (!nameList.isEmpty()) {
            names.setLength(names.length() - 2);
            state.sendingBlockingQueue.add(message.setResponse(names.toString()));
        } else {
            state.sendingBlockingQueue.add(message.setResponse("@%s, no alternate names found in logs PEEPERS".formatted(message.getSender())));
        }
    }

    private void choose(CommandMessage message) {
        String[] choices = message.getMessageWithoutCommand().split(" ");
        if (choices.length == 0) {
            return;
        }
        int choice = ThreadLocalRandom.current().nextInt(choices.length);
        state.sendingBlockingQueue.add(message.setResponse(String.format("@%s, I choose %s", message.getSender(), choices[choice])));
    }

    private void ping(CommandMessage message) {
        state.sendingBlockingQueue.add(message.setResponse(String.format(
                "NamBot online for %s | %d messages sent | %d messages logged | %d timeouts logged, of which %d were permabans.",
                (Utils.convertTime(Instant.now().minus(ConsoleMain.getStartTime().toEpochMilli(), ChronoUnit.MILLIS).toEpochMilli() / 1000)),
                state.getSentMessageCount(), state.getMessageCount(), state.getTimeoutCount(),
                state.getPermabanCount())));

    }

    private void searchUser(CommandMessage message) {
        long count = databaseHandler.searchUser(message.getUsername(),
                message.getMessageWithoutUsername());
        if (count == 0) {
            state.sendingBlockingQueue.add(message.setResponse("@%s, %s".formatted(message.getSender(), Response.NO_MESSAGES)));
        } else {
            state.sendingBlockingQueue.add(message.setResponse("@%s, %s has used %s in %d messages".formatted(
                    message.getSender(),
                    message.getUsername(),
                    Utils.getWordList(message.getMessageWithoutUsername()),
                    count)));
        }
    }

    private void search(CommandMessage message) {
        long count = databaseHandler.search(message.getMessageWithoutCommand());
        if (count == 0) {
            state.sendingBlockingQueue.add(message.setResponse("@%s, %s".formatted(message.getSender(), Response.NO_MESSAGES)));
        } else {
            state.sendingBlockingQueue.add(message.setResponse(
                    "@%s found %s in %d rows.".formatted(
                            message.getSender(),
                            Utils.getWordList(message.getMessageWithoutCommand()),
                            count)));
        }
    }

    private void firstOccurrence(CommandMessage message) {
        state.sendingBlockingQueue.add(message.setResponse(
                "@%s, %s".formatted(
                        message.getSender(),
                        databaseHandler
                                .firstOccurrence(message.getMessageWithoutCommand())
                                .orElse(Response.NO_MESSAGES.toString()))));
    }

    private void randomSearch(CommandMessage message) {
        String result = databaseHandler.randomSearch(message.getUsername(),
                message.getMessageWithoutUsername()).orElse(Response.NO_MESSAGES.toString());
        if (!result.startsWith("[")) {
            result = "%s, %s".formatted(message.getSender(), result);
        }
        state.sendingBlockingQueue.add(message.setResponse(result));
    }

    private void randomQuote(CommandMessage message) {
        if (sendIfNoMessages(message)) {
            return;
        }
        String result = databaseHandler.randomQuote(message.getUsername(), message.getYear())
                .orElse(Response.NO_MESSAGES.toString());
        if (!result.startsWith("[")) {
            result = "%s, %s".formatted(message.getSender(), result);
        }
        state.sendingBlockingQueue.add(message.setResponse(result));
    }

    private boolean throwIfBot(String username) {
        if (username.equalsIgnoreCase(botName)) {
            state.sendingBlockingQueue.add(new SimpleMessage("isBot", "PepeSpin"));
            return true;
        }
        return false;
    }

    private void userNam(CommandMessage message) {
        int count = getCount(message.getUsername());
        if (count == 0) {
            return;
        }

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
        if (sendIfNoMessages(message)) {
            return;
        }

        state.sendingBlockingQueue.add(message.setResponse("@%s, %s".formatted(message.getSender(), databaseHandler.firstMessage(message.getUsername()))));
    }

    private void lastMessage(CommandMessage message) {
        if (sendIfNoMessages(message)) {
            return;
        }
        state.sendingBlockingQueue.add(
                message.setResponse("@%s, %s".formatted(message.getSender(),
                        databaseHandler.lastMessage(message.getUsername()))));
    }

    private void lastSeen(CommandMessage message) {
        if (sendIfNoMessages(message)) {
            return;
        }

        state.sendingBlockingQueue.add(message.setResponse("@%s, %s".formatted(message.getSender(),
                databaseHandler.lastSeen(message.getUsername()))));
    }

    private boolean sendIfNoMessages(CommandMessage message) {
        int count = getCount(message.getUsername());
        if (count == 0) {
            log.info("Did not find any messages for user {}", message.getUsername());
            state.sendingBlockingQueue.add(message.setResponse("@%s, %s".formatted(message.getSender(), Response.NO_MESSAGES)));
            return true;
        }
        return false;
    }

    private void topNammers(CommandMessage message) {
        state.sendingBlockingQueue.add(
                message.setResponse(databaseHandler.getTopTimeouts()
                        .orElse("@%s, %s".formatted(message.getSender(), Response.INTERNAL_ERROR))));
    }

    private void messageCount(CommandMessage message) {
        int count = getCount(message.getUsername());
        if (count == 0) {
            state.sendingBlockingQueue.add(message.setResponse("@%s, %s".formatted(message.getSender(), Response.NO_MESSAGES)));
            return;
        }
        if (message.getSender().equalsIgnoreCase(message.getUsername())) {
            state.sendingBlockingQueue.add(message.setResponse("@%s, you have sent %s messages in this chat".formatted(message.getSender(), count)));
        } else {
            state.sendingBlockingQueue.add(message.setResponse("@%s, %s has sent %s messages in this chat".formatted(message.getSender(), message.getUsername(), count)));
        }
    }

    private int getCount(String username) {
        return databaseHandler.getMessageCount(username);
    }

    private void initializeMods() {
        this.mods = new HashSet<>();
        this.admins = new HashSet<>();
        mods.addAll(databaseHandler.getModList());
        admins.add(admin);
        admins.add("Autoban");
        admins.add(channel.replace("#", ""));
        log.info("Initialized mods list {} | admins {}.", mods, admins);
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
        state.sendingBlockingQueue.add(message.setResponse("@" + message.getSender() + ", added " + alt + " as " + main + "'s alt account."));
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
                                && state.online.get())))
        {
            state.sendingBlockingQueue.add(
                    message.setResponse("@" + message.getSender() + ", added " + message.getUsername() + " to ignore list."));
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
                .isBefore(Instant.now()))
        {
            state.sendingBlockingQueue.add(message.setResponse("@" + message.getSender() + ", removed " + message.getUsername() + " from ignore list."));
        }
        databaseHandler.removeDisabled(message.getUsername());
        log.info("{} removed {} from disabled list", message.getSender(), message.getUsername());

    }

    private void getLogs(CommandMessage message) {
        if (!message.getUsername().equalsIgnoreCase("all") && sendIfNoMessages(message)) {
            return;
        }

        final String logSite = website + "/logs/";
        String response = String.format("@%s logs for %s: %s%s", message.getSender(),
                message.getUsername(), logSite,
                message.getUsername());

        state.sendingBlockingQueue.add(message.setResponse(response));
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
        previousMessageTimes.removeIf(instant -> instant.plus(300, ChronoUnit.SECONDS).isAfter(Instant.now()));
        return previousMessageTimes.size() >= 5;
    }

    public boolean isAllowed(CommandMessage message) {
        String from = message.getSender();
        String username = message.getUsername();
        Command command = message.getCommand();

        //admins are not affected by any rules except optout rules.
        if (admins.stream().anyMatch(from::equalsIgnoreCase) && !command.isOptedOut(username, state.disabledUsers)) {
            return true;
        }

        if (command.isAdminOnly()) {
            return false;
        }

        if (isBanned(from)) {
            return false;
        }

        //online check
        if (state.online.get() && !command.isOnlineAllowed()) {
            log.info("Attempted to use {} while stream is online", command);
            return false;
        }

        //10 second cooldown
        if (lastCommandTime.plus(10, ChronoUnit.SECONDS).isAfter(Instant.now())
                && admins.stream().noneMatch(from::equalsIgnoreCase)
                //These commands have their own logic for cooldown.
                && command != Command.ADDDISABLED && command != Command.REMDISABLED)
        {
            log.info("Attempted to use {} while cooldown active", command);
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

        if (throwIfBot(username)) {
            return false;
        }

        //wildcard in username not allowed
        if (username.contains("*") || username.contains("?") || username.contains("~")
                || username.contains("{")
                || username.contains("["))
        {
            return false;
        }

        if (command.isOptedOut(username, state.disabledUsers)) {
            state.sendingBlockingQueue.add(message.setResponse("@" + from + ", that user has been removed from the " + command
                    + " command. Type !adddisabled to remove yourself or !remdisabled to re-enable commands."));
            lastCommandTime = Instant.now();
            return false;
        }

        if (mods.stream().anyMatch(from::equalsIgnoreCase)) {
            return true;
        }

        //check database for user specific command permissions
        Map<String, Boolean> userPermissionMap = databaseHandler.getPersonalPermissions(from);
        Boolean specified = command.isUserCommandSpecified(userPermissionMap);
        if (specified != null) {
            log.info("User {} command specific permission was {}.", from, specified);
            return specified;
        }

        if (command.isNoArgs()) {
            return true;
        }

        if (from.equalsIgnoreCase(username)) {
            return command.isSelfAllowed();
        }

        return command.isOthersAllowed();
    }

    private boolean isBanned(String from) {
        //previous spammer check
        if (banned.containsKey(from.toLowerCase())) {
            if (banned.get(from).plus(600, ChronoUnit.SECONDS).isAfter(Instant.now())) {
                log.info("Banned user {} attempted to use a command.", from);
                return true;
            } else {
                banned.remove(from);
            }
        }

        //manually banned check
        if (manualBanned.containsKey(from.toLowerCase())) {
            if (manualBanned.get(from).plus(3600, ChronoUnit.SECONDS).isAfter(Instant.now())) {
                log.info("Manually banned user {} attempted to use a command.", from);
                return true;
            } else {
                manualBanned.remove(from);
            }
        }
        return false;
    }
}
