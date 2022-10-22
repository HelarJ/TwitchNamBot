package chatbot.service;

import chatbot.ConsoleMain;
import chatbot.dao.ApiHandler;
import chatbot.dao.DatabaseHandler;
import chatbot.dao.FtpHandler;
import chatbot.dataclass.Message;
import chatbot.enums.Command;
import chatbot.singleton.SharedStateSingleton;
import chatbot.utils.Config;
import chatbot.utils.Utils;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import lombok.extern.log4j.Log4j2;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Log4j2
public class CommandHandlerService extends AbstractExecutionThreadService {
    private Instant lastCommandTime = Instant.now().minus(30, ChronoUnit.SECONDS);
    private final HashMap<String, Instant> banned = new HashMap<>();
    private final HashMap<String, Instant> superbanned = new HashMap<>();
    private final String website = Config.getBotWebsite();
    private final String botName = Config.getTwitchUsername();
    private List<String> godUsers;
    private List<String> mods;
    private final String admin = Config.getBotAdmin();
    private final String channel = Config.getChannelToJoin();
    private final List<Instant> previousMessageTimes = new ArrayList<>();
    private String previousMessage = "";
    private final DatabaseHandler sqlSolrHandler;
    private final ApiHandler apiHandler;
    private final SharedStateSingleton state = SharedStateSingleton.getInstance();

    public CommandHandlerService(DatabaseHandler databaseHandler, ApiHandler apiHandler) {
        this.sqlSolrHandler = databaseHandler;
        this.apiHandler = apiHandler;
        FtpHandler ftpHandler = new FtpHandler();
        ftpHandler.cleanLogs();
        refreshLists("Startup");
    }

    @Override
    protected void shutDown() {
        log.debug("{} stopped.", CommandHandlerService.class);
    }

    @Override
    protected void startUp() {
        log.debug("{} started.", CommandHandlerService.class);
    }

    public void refreshLists(String from) {
        if (from.equals("Startup") || mods.contains(from)) {
            initializeMods();
            initializeDisabled();
            initializeAlts();
            initializeBlacklist();
            if (!from.equals("Startup")) {
                state.sendingBlockingQueue.add(new Message("Lists refreshed HACKERMANS"));
            }
        }
    }

    @Override
    public void run() throws InterruptedException {
        while (state.isBotStillRunning()) {
            Message message = state.commandHandlerBlockingQueue.take();
            if (message.isPoison()) {
                log.debug(CommandHandlerService.class + " poisoned.");
                break;
            }
            handleCommand(message);
        }
    }

    private void handleCommand(Message message) {
        Command command = message.getCommand();
        if (command == null) {
            return;
        }

        String from = message.getSender();
        String argStr = message.getArguments();
        log.info(String.format("%s used %s with arguments [%s].", from, command, argStr));

        String username = Utils.getArg(argStr, 0);
        if (username == null) {
            username = from;
        }
        username = Utils.cleanName(from, username);

        if (!isAllowed(from, username, command)) {
            log.info(from + " not allowed to use command " + command);
            return;
        }

        switch (command) {
            case NAMMERS -> topNammers();
            case NAMPING -> ping();
            case NAMBAN -> ban(username);
            case NAMES -> names(from, username);
            case NAMREFRESH -> refreshLists(from);
            case NAMCOMMANDS -> namCommands(from);
            case NAMCHOOSE -> choose(argStr, from);
            case NAM -> userNam(from, username);
            case LASTMESSAGE -> lastMessage(from, username);
            case FIRSTMESSAGE -> firstMessage(from, username);
            case LOG, LOGS -> getLogs(from, username);
            case RQ -> randomQuote(from, username, argStr);
            case RS -> randomSearch(from, argStr);
            case ADDDISABLED -> addDisabled(from, username);
            case REMDISABLED -> removeDisabled(from, username);
            case FS -> firstOccurrence(from, argStr);
            case SEARCH -> search(from, argStr);
            case SEARCHUSER -> searchUser(from, argStr);
            case ADDALT -> addAlt(from, argStr);
            case STALKLIST -> getFollowList(from, username);
        }
        lastCommandTime = Instant.now();
    }

    private void namCommands(String name) {
        state.sendingBlockingQueue.add(new Message("@" + name + ", commands for this bot: " + website.substring(0, website.length() - 5) + "/commands"));

    }

    private void ban(String username) {
        superbanned.put(username, Instant.now());
        state.sendingBlockingQueue.add(new Message("Banned " + username + " from using the bot for 1h."));
    }

    private void names(String from, String username) {
        StringBuilder names = new StringBuilder("@");
        names.append(from).append(", ").append(username).append("'s other names are: ");
        var nameList = sqlSolrHandler.getAlternateNames(username);
        for (String name : nameList) {
            names.append(name).append(", ");
        }
        if (nameList.size() >= 1) {
            names.setLength(names.length() - 2);
            state.sendingBlockingQueue.add(new Message(names.toString()));
        } else {
            state.sendingBlockingQueue.add(new Message("@" + from + ", no alternate names found in logs PEEPERS"));
        }
    }

    private void choose(String choiceMsg, String from) {
        String[] choices = choiceMsg.split(" ");
        if (choices.length == 0) {
            return;
        }
        int choice = ThreadLocalRandom.current().nextInt(0, choices.length);
        state.sendingBlockingQueue.add(new Message(String.format("@%s, I choose %s", from, choices[choice])));
    }

    private void ping() {
        state.sendingBlockingQueue.add(new Message(
                String.format("NamBot online for %s | %d messages sent | %d messages logged | %d timeouts logged, of which %d were permabans.",
                        (Utils.convertTime((int) (Instant.now().minus(ConsoleMain.getStartTime().toEpochMilli(), ChronoUnit.MILLIS).toEpochMilli() / 1000))),
                        state.getSentMessageCount(),
                        state.getMessageCount(),
                        state.getTimeoutCount(),
                        state.getPermabanCount()
                )));
    }

    private void searchUser(String from, String msg) {
        String username = Utils.getArg(msg, 0);
        if (username == null) {
            return;
        }
        msg = Utils.getMsgWithoutName(msg, username);
        username = Utils.cleanName(from, username);

        long count = sqlSolrHandler.searchUser(username, msg);
        if (count == 0) {
            state.sendingBlockingQueue.add(new Message("@" + from + ", no messages found PEEPERS"));
        } else {
            state.sendingBlockingQueue.add(new Message("@" + from + ", " + username + " has used " + Utils.getWordList(msg) + " in " + count + " messages."));
        }
    }

    private void search(String from, String msg) {
        long count = sqlSolrHandler.search(msg);
        if (count == 0) {
            state.sendingBlockingQueue.add(new Message("@" + from + ", no messages found PEEPERS"));
        } else {
            state.sendingBlockingQueue.add(new Message("@" + from + " found " + Utils.getWordList(msg) + " in " + count + " rows."));
        }
    }

    private void firstOccurrence(String from, String msg) {
        state.sendingBlockingQueue.add(new Message("@%s, %s".formatted(from, sqlSolrHandler.firstOccurrence(msg))));
    }

    private void randomSearch(String from, String msg) {
        String username = Utils.getArg(msg, 0);
        if (username == null || username.length() == 0) {
            return;
        }

        msg = Utils.getMsgWithoutName(msg, username);
        username = Utils.cleanName(from, username);

        String result = sqlSolrHandler.randomSearch(username, msg);
        if (!result.startsWith("[")) {
            result = "%s, %s".formatted(from, result);
        }
        state.sendingBlockingQueue.add(new Message(result));
    }

    private void randomQuote(String from, String username, String args) {
        if (hasNoMessages(from, username)) {
            return;
        }
        String year = Utils.getYear(Utils.getArg(args, 1));

        String result = sqlSolrHandler.randomQuote(username, year);
        if (!result.startsWith("[")) {
            result = "%s, %s".formatted(from, result);
        }
        state.sendingBlockingQueue.add(new Message(result));
    }

    private boolean isBot(String username) {
        if (username.toLowerCase().equals(botName)) {
            state.sendingBlockingQueue.add(new Message("PepeSpin"));
            return true;
        }
        return false;
    }

    private void userNam(String from, String username) {
        int timeout = sqlSolrHandler.getTimeoutAmount(username);
        if (timeout > 0) {
            Message message;
            if (from.equals(username)) {
                message = new Message(String.format("@%s, you have spent %s in the shadow realm.", username, Utils.convertTime(timeout)));
            } else {
                message = new Message(String.format("%s has spent %s in the shadow realm.", username, Utils.convertTime(timeout)));
            }
            state.sendingBlockingQueue.add(message);

        }
        lastCommandTime = Instant.now();
    }

    private void firstMessage(String from, String username) {
        if (hasNoMessages(from, username)) {
            return;
        }
        state.sendingBlockingQueue.add(new Message("%s, %s".formatted(from, sqlSolrHandler.firstMessage(username))));
    }

    private void lastMessage(String from, String username) {
        if (hasNoMessages(from, username)) {
            return;
        }
        state.sendingBlockingQueue.add(new Message("@%s, %s".formatted(from, sqlSolrHandler.lastMessage(username))));
    }

    private boolean hasNoMessages(String from, String username) {
        int count = getCount(username);
        if (count == 0) {
            log.info("Did not find any messages for user " + username);
            state.sendingBlockingQueue.add(new Message("@" + from + ", no messages found PEEPERS"));
            return true;
        }
        return false;
    }

    private void topNammers() {
        String topTimeoutList = sqlSolrHandler.getTopTimeouts();
        if (topTimeoutList == null) {
            return;
        }
        state.sendingBlockingQueue.add(new Message(topTimeoutList));
    }

    private int getCount(String username) {
        return sqlSolrHandler.getMessageCount(username);
    }

    private void initializeMods() {
        this.mods = new ArrayList<>();
        this.godUsers = new ArrayList<>();
        mods.add(admin);
        mods.add(channel.replace("#", ""));
        mods.add("autoban");
        mods.addAll(sqlSolrHandler.getModList());
        godUsers.add(admin);
        godUsers.add(channel.replace("#", ""));
        log.info("Initialized mods list {} | god users {}.", mods, godUsers);
    }

    private void addAlt(String from, String args) {
        String main = Utils.getArg(args, 0);
        if (main == null) {
            return;
        }
        main = main.toLowerCase();
        String alt = Utils.getArg(args, 1);
        if (alt == null) {
            return;
        }
        alt = alt.toLowerCase();
        if (state.alts.containsKey(main)) {
            if (state.alts.get(main).contains(alt)) {
                return;
            }
        }
        log.info("{} adding {} to {}'s alt list", from, alt, main);
        if (!sqlSolrHandler.addAlt(main, alt)) {
            log.error("Adding alt was unsuccessful: {} - {}.", main, alt);
            state.sendingBlockingQueue.add(new Message("Internal error Deadlole"));
            return;
        }
        state.mains.put(alt, main);
        state.mains.putIfAbsent(main, main);
        state.alts.putIfAbsent(main, new ArrayList<>());
        state.alts.get(main).add(alt);
        state.sendingBlockingQueue.add(new Message("@" + from + ", added " + alt + " as " + main + "'s alt account."));
    }

    void addDisabled(String from, String username) {
        if (state.disabledUsers.contains(username)) {
            return;
        }

        state.disabledUsers.add(username.toLowerCase());
        sqlSolrHandler.addDisabled(from, username);

        if (!from.equals("Autoban") && (mods.contains(from.toLowerCase()) ||
                lastCommandTime.plus(10, ChronoUnit.SECONDS).isBefore(Instant.now())))
        {
            state.sendingBlockingQueue.add(new Message("@" + from + ", added " + username + " to ignore list."));
        }
        log.info("{} added {} to disabled list", from, username);
    }

    private void removeDisabled(String from, String username) {
        if (!state.disabledUsers.contains(username)) {
            return;
        }
        state.disabledUsers.remove(username.toLowerCase());
        if (mods.contains(from.toLowerCase()) || lastCommandTime.plus(10, ChronoUnit.SECONDS).isBefore(Instant.now())) {
            state.sendingBlockingQueue.add(new Message("@" + from + ", removed " + username + " from ignore list."));
        }
        sqlSolrHandler.removeDisabled(username);
        log.info("{} removed {} from disabled list", from, username);

    }

    private void getFollowList(String from, String username) {
        String content = apiHandler.getFollowList(username);
        if (content == null) {
            log.info("No follow list found for {}", username);
            state.sendingBlockingQueue.add(new Message("@%s, no such user found PEEPERS".formatted(username)));
            return;
        }
        String link = "stalk_" + username;
        new Thread(() -> {
            FtpHandler ftpHandler = new FtpHandler();
            if (ftpHandler.upload(link, content)) {
                state.sendingBlockingQueue.add(
                        new Message("@%s all channels followed by %s: %s%s".formatted(from, username, website, link)));
            }
        }).start();
    }

    private void getLogs(String from, String username) {
        state.logCache.putIfAbsent(username, 0);
        int count = getCount(username);
        if (count == 0) {
            log.info("Did not find any logs for user " + username);
            state.sendingBlockingQueue.add(new Message("@" + from + ", no messages found PEEPERS"));
            return;
        }
        if (count == state.logCache.get(username)) {
            String output = String.format("@%s logs for %s: %s%s", from, username, website, username);
            log.info("No change to message count, not updating link.");
            state.sendingBlockingQueue.add(new Message(output));
            return;
        } else {
            state.logCache.put(username, count);
        }

        Instant startTime = Instant.now();
        String logs = sqlSolrHandler.getLogs(username, count);
        if (logs == null) {
            return;
        }

        new Thread(() -> {
            FtpHandler ftpHandler = new FtpHandler();
            if (ftpHandler.upload(username, logs)) {
                String output = String.format("@%s logs for %s: %s%s", from, username, website, username);
                log.info(output);
                log.info("Total time: " + (Utils.convertTime((int) (Instant.now().minus(startTime.toEpochMilli(), ChronoUnit.MILLIS).toEpochMilli() / 1000))));
                state.sendingBlockingQueue.add(new Message(output));
            }
        }).start();
    }

    private void initializeBlacklist() {
        ArrayList<String> blacklist = new ArrayList<>();
        ArrayList<String> textBlacklist = new ArrayList<>();
        StringBuilder replacelistSb = new StringBuilder();
        Map<String, String> map = sqlSolrHandler.getBlacklist();
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
        state.setDisabledUsers(sqlSolrHandler.getDisabledList());
        log.info("Disabled list initialized. {} disabled users.", state.disabledUsers.size());
    }

    private void initializeAlts() {
        state.mains = new HashMap<>();
        state.alts = new HashMap<>();

        List<String> mainsAltsCsv = sqlSolrHandler.getAltsList();

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

    private boolean isAllowed(String from, String username, Command command) {
        //admins are not affected by any rules except optout rules.
        if (godUsers.contains(from.toLowerCase())
                && !command.isOptedOut(username, state.disabledUsers))
        {
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
        if (lastCommandTime.plus(10, ChronoUnit.SECONDS).isAfter(Instant.now())
                && !godUsers.contains(from)
                //These commands have their own logic for cooldown.
                && command != Command.ADDDISABLED
                && command != Command.REMDISABLED)
        {
            return false;
        }

        //new spammer check
        if (checkOneManSpam(from)) {
            banned.put(from, Instant.now());
            state.sendingBlockingQueue.add(new Message("@" + from + ", stop one man spamming. Banned from using commands for 10 minutes peepoD"));
            return false;
        }

        if (isBot(username)) {
            return false;
        }

        //wildcard in username not allowed
        if (username.contains("*") || username.contains("?") || username.contains("~") || username.contains("{")
                || username.contains("["))
        {
            return false;
        }

        if (command.isOptedOut(username, state.disabledUsers)) {
            state.sendingBlockingQueue.add(
                    new Message("@" + from + ", that user has been removed from the "
                            + command
                            + " command. Type !adddisabled to remove yourself or !remdisabled to re-enable commands."));
            lastCommandTime = Instant.now();
            return false;
        }

        return command.isSelfAllowed(from, username)
                || command.isOthersAllowed()
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
