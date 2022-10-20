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
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ThreadLocalRandom;

import static chatbot.enums.Command.FIRSTMESSAGE;
import static chatbot.enums.Command.LASTMESSAGE;
import static chatbot.enums.Command.RQ;
import static chatbot.enums.Command.RS;

@Log4j2
public class CommandHandlerService extends AbstractExecutionThreadService {
    private final String solrCredentials = Config.getSolrCredentials();
    private Instant lastCommandTime = Instant.now().minus(30, ChronoUnit.SECONDS);
    private final String SQLCredentials = Config.getSQLCredentials();
    private final HashMap<String, Integer> logCache = new HashMap<>();
    private final HashMap<String, Instant> banned = new HashMap<>();
    private final HashMap<String, Instant> superbanned = new HashMap<>();
    private final String website = Config.getBotWebsite();
    private final String botName = Config.getTwitchUsername();
    private List<String> godUsers;
    private List<String> mods;
    private final String admin = Config.getBotAdmin();
    private final String channel = Config.getChannelToJoin();
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
            Message message = state.messageBlockingQueue.take();
            if (message.isPoison()) {
                log.debug(CommandHandlerService.class + " poisoned.");
                break;
            }
            handleCommand(message);
        }
    }

    public void handleCommand(Message message) {
        Command command = message.getCommand();
        if (command == null) {
            return;
        }

        String name = message.getSender();
        String argStr = message.getArguments();
        log.info(String.format("%s used %s with arguments [%s].", name, command, argStr));
        if (isNotAllowed(name, argStr, command)) {
            log.info(name + " not allowed to use command " + command);
            return;
        }

        switch (command) {
            case NAMMERS -> sendTop10to();
            case NAMPING -> ping();
            case NAMBAN -> ban(name, argStr);
            case NAMES -> names(name, argStr);
            case NAMREFRESH -> refreshLists(name);
            case NAMCOMMANDS -> namCommands(name);
            case NAMCHOOSE -> choose(argStr, name);
            case NAM -> userNam(name, argStr);
            case LASTMESSAGE -> lastMessage(name, argStr);
            case FIRSTMESSAGE -> firstMessage(name, argStr);
            case LOG, LOGS -> getLogs(argStr, name);
            case RQ -> randomQuote(name, argStr);
            case RS -> randomSearch(name, argStr);
            case ADDDISABLED -> addDisabled(name, argStr);
            case REMDISABLED -> removeDisabled(name, argStr);
            case FS -> firstOccurrence(name, argStr);
            case SEARCH -> search(name, argStr);
            case SEARCHUSER -> searchUser(name, argStr);
            case ADDALT -> addAlt(name, argStr);
            case STALKLIST -> getFollowList(argStr, name);
        }
        lastCommandTime = Instant.now();
    }

    private void namCommands(String name) {
        state.sendingBlockingQueue.add(new Message("@" + name + ", commands for this bot: " + website.substring(0, website.length() - 5) + "/commands"));

    }

    private void ban(String from, String args) {
        if (godUsers.contains(from.toLowerCase())) {
            String username = Utils.getArg(args, 0);
            if (username == null) {
                return;
            }
            username = Utils.cleanName(from, args);

            superbanned.put(username, Instant.now());
            state.sendingBlockingQueue.add(new Message("Banned " + username + " from using the bot for 1h."));
        }
    }

    private void names(String from, String args) {
        String username = Utils.getArg(args, 0);
        if (username == null) {
            return;
        }
        username = Utils.cleanName(from, username);
        StringBuilder names = new StringBuilder("@");
        names.append(from).append(", ").append(username).append("'s other names are: ");
        var nameList = sqlSolrHandler.getAlternateNames(username);
        var size = nameList.size();
        for (String name : nameList) {
            names.append(name);
            names.append(", ");
        }
        if (size >= 1) {
            names.reverse();
            names.deleteCharAt(0).deleteCharAt(0);
            names.reverse();
            state.sendingBlockingQueue.add(new Message(names.toString()));
        } else {
            state.sendingBlockingQueue.add(new Message("@" + from + ", no alternate names found in logs PEEPERS"));
        }
    }

    private void choose(String choiceMsg, String from) {
        choiceMsg = choiceMsg.replaceAll(" \uDB40\uDC00", "");
        String[] choices = choiceMsg.split(" ");
        if (choices.length == 0) {
            return;
        }
        int choice = ThreadLocalRandom.current().nextInt(0, choices.length);
        state.sendingBlockingQueue.add(new Message(String.format("@%s, I choose %s", from, choices[choice])));
        lastCommandTime = Instant.now();
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

        msg = getMsgWithoutName(msg, username);

        username = Utils.cleanName(from, username);

        String phrase = Utils.getSolrPattern(msg);

        if (isBot(from, username)) {
            return;
        }

        try (SolrClient solr = new HttpSolrClient.Builder(solrCredentials).build()) {
            SolrQuery query = new SolrQuery();
            query.set("q", phrase + " AND username:" + username + " AND -username:" + botName + " AND -message:\"!search\" AND -message:\"!searchuser\" AND -message:\"!rs\"");
            //query.set("fl", "*,ttf(message,"+phrase+")");
            query.set("rows", 1);
            QueryResponse response = solr.query(query);
            String finalMessage = "@" + from + ", no messages found PEEPERS";
            try {
                SolrDocumentList result = response.getResults();
                long rowcount = result.getNumFound();
                finalMessage = "@" + from + ", " + username + " has used " + Utils.getWordList(msg) + " in " + rowcount + " messages.";
                SolrDocument result1 = response.getResults().get(0);
                /*
                long wordcount = (long) result1.get("ttf(message,"+phrase+")");
                if (wordcount > 0){
                    finalMessage = "@"+from+", "+username.charAt(0)+zws1+zws2+username.substring(1)+" has used "+phrase+" in "+rowcount+" messages. Total count: "+wordcount+".";
                }*/
            } catch (IndexOutOfBoundsException ignored) {
            }
            state.sendingBlockingQueue.add(new Message(finalMessage));

        } catch (IOException | SolrServerException | BaseHttpSolrClient.RemoteSolrException e) {
            log.error(e.getMessage());
        }
    }

    private static String getMsgWithoutName(String msg, String username) {
        try {
            if (msg.startsWith("me ".toLowerCase())) {
                msg = msg.substring(3);
            } else {
                msg = msg.substring(username.length() + 1);
            }
        } catch (StringIndexOutOfBoundsException e) {
            msg = "*";
        }
        return msg;
    }

    private void search(String from, String msg) {
        String phrase = Utils.getSolrPattern(msg);

        try (SolrClient solr = new HttpSolrClient.Builder(solrCredentials).build()) {
            SolrQuery query = new SolrQuery();
            query.set("q", phrase + " AND -username:" + botName + " AND -message:\"!search\" AND -message:\"!searchuser\" AND -message:\"!rs\"");
            //query.set("fl", "*,ttf(message,"+phrase+")");
            query.set("rows", 1);
            QueryResponse response = solr.query(query);
            String finalMessage = "@" + from + ", no messages found PEEPERS";
            try {
                SolrDocumentList result = response.getResults();
                long rowcount = result.getNumFound();
                finalMessage = "@" + from + " found " + Utils.getWordList(msg) + " in " + rowcount + " rows.";
                SolrDocument result1 = response.getResults().get(0);
                /*long wordcount = (long) result1.get("ttf(message,"+phrase+")");
                if (wordcount > 0){
                    finalMessage = "@"+from+" found word "+msg+" in "+rowcount+" rows. Total count: "+wordcount+".";
                }*/
            } catch (IndexOutOfBoundsException ignored) {
            }
            state.sendingBlockingQueue.add(new Message(finalMessage));

        } catch (IOException | BaseHttpSolrClient.RemoteSolrException | SolrServerException e) {
            log.error(e.getMessage());
        }
    }

    private void firstOccurrence(String from, String msg) {
        if (!godUsers.contains(from)) {
            return;
        }
        state.sendingBlockingQueue.add(new Message(sqlSolrHandler.firstOccurrence(from, msg)));
    }

    private void randomSearch(String from, String msg) {
        String username = Utils.getArg(msg, 0);
        if (username == null || username.length() == 0) {
            return;
        }

        msg = getMsgWithoutName(msg, username);

        username = Utils.cleanName(from, username);

        if (!godUsers.contains(from) && !username.equalsIgnoreCase(from)) {
            return;
        }

        if (isBot(from, username)) {
            return;
        }

        state.sendingBlockingQueue.add(new Message(sqlSolrHandler.randomSearch(from, username, msg)));
    }

    private boolean isBot(String from, String username) {
        if (username.toLowerCase().equals(botName)) {
            state.sendingBlockingQueue.add(new Message("PepeSpin"));
            return true;
        }

        if (!godUsers.contains(from) && (username.contains("*") || username.contains("?") || username.contains("~") || username.contains("{")
                || username.contains("[")))
        {
            state.sendingBlockingQueue.add(new Message("@" + from + ", no wildcards allowed in username NOPERS"));
            return true;
        }
        return false;
    }

    public void userNam(String from, String args) {
        if (state.online.get()) {
            log.info("Attempted to use !NaM while stream is online");
            return;
        }
        if (lastCommandTime.plus(2, ChronoUnit.SECONDS).isAfter(Instant.now())) {
            log.info("Attempted to use !NaM before cooldown was over");
            return;
        }

        String username = Utils.getArg(args, 0);
        if (username == null) {
            username = from;
        }
        int timeout = sqlSolrHandler.getTimeoutAmount(username);
        if (timeout > 0) {
            if (from.equals(username)) {
                state.sendingBlockingQueue.add(new Message(String.format("%s, you have spent %s in the shadow realm.", username, Utils.convertTime(timeout))));
            } else {
                state.sendingBlockingQueue.add(new Message(String.format("%s has spent %s in the shadow realm.", username, Utils.convertTime(timeout))));
            }

        }
        lastCommandTime = Instant.now();
    }

    public void firstMessage(String from, String args) {
        String username = Utils.getArg(args, 0);
        if (username == null) {
            username = from;
        }
        username = Utils.cleanName(from, username);
        if (hasNoMessages(from, username)) {
            return;
        }

        try (Connection conn = DriverManager.getConnection(SQLCredentials);
             PreparedStatement stmt = conn.prepareStatement("call chat_stats.sp_get_first_message(?)"))
        {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String convertedTime = Utils.convertTime((int) (Instant.now().minus(rs.getTimestamp("time",
                                Calendar.getInstance(TimeZone.getTimeZone("UTC"))).getTime(),
                        ChronoUnit.MILLIS).toEpochMilli() / 1000));
                String message = rs.getString("message");
                String finalMessage = String.format("%s's first message %s ago was: %s",
                        username,
                        convertedTime,
                        message);
                state.sendingBlockingQueue.add(new Message(finalMessage));
            }

        } catch (SQLException e) {
            log.error("SQLException: {}, VendorError: {}", e.getMessage(), e.getErrorCode());
        }
    }

    public void lastMessage(String from, String args) {
        String username = Utils.getArg(args, 0);
        if (username == null) {
            return;
        }
        username = Utils.cleanName(from, username);

        if (hasNoMessages(from, username)) {
            return;
        }

        try (Connection conn = DriverManager.getConnection(SQLCredentials);
             PreparedStatement stmt = conn.prepareStatement("call chat_stats.sp_get_last_message(?)"))
        {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String convertedTime = Utils.convertTime((int) (Instant.now().minus(rs.getTimestamp("time",
                                Calendar.getInstance(TimeZone.getTimeZone("UTC"))).getTime(),
                        ChronoUnit.MILLIS).toEpochMilli() / 1000));
                String message = rs.getString("message");

                String finalMessage = String.format("%s's last message %s ago was: %s",
                        username,
                        convertedTime,
                        message);
                state.sendingBlockingQueue.add(new Message(finalMessage));
            }

        } catch (SQLException e) {
            log.error("SQLException: {}, VendorError: {}", e.getMessage(), e.getErrorCode());
        } finally {
            lastCommandTime = Instant.now();
        }
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

    private void sendTop10to() {
        try (Connection conn = DriverManager.getConnection(SQLCredentials);
             Statement stmt = conn.createStatement())
        {
            ResultSet results = stmt.executeQuery("call chat_stats.sp_get_top10to()");
            StringBuilder sb = new StringBuilder();
            sb.append("Top NaMmers: ");
            while (results.next()) {
                sb.append(results.getString("username"));
                sb.append(": ");
                sb.append(Utils.convertTime(results.getInt("timeout")));
                sb.append(" | ");
                if (sb.length() >= 270) {
                    break;
                }
            }
            state.sendingBlockingQueue.add(new Message(sb.toString()));
        } catch (SQLException e) {
            log.error("SQLException: {}, VendorError: {}", e.getMessage(), e.getErrorCode());
        }
    }

    private int getCount(String username) {
        int count = 0;
        String stmtStr = "call chat_stats.sp_get_message_count(?)";
        if (username.equalsIgnoreCase("all")) {
            stmtStr = "call chat_stats.sp_get_all_count(?)";
        }

        try (Connection conn = DriverManager.getConnection(SQLCredentials);
             PreparedStatement stmt = conn.prepareStatement(stmtStr))
        {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                count = rs.getInt("count");
            }

        } catch (SQLException e) {
            log.error("SQLException: {}, VendorError: {}", e.getMessage(), e.getErrorCode());
        }
        return count;
    }

    private void initializeMods() {
        this.mods = new ArrayList<>();
        this.godUsers = new ArrayList<>();
        mods.add(admin);
        mods.add(channel.replace("#", ""));
        mods.add("autoban");
        try (Connection conn = DriverManager.getConnection(SQLCredentials);
             PreparedStatement stmt = conn.prepareStatement("call chat_stats.sp_get_mods()"))
        {
            stmt.execute();
            ResultSet rs = stmt.getResultSet();

            while (rs.next()) {
                String name = rs.getString("name");
                mods.add(name.toLowerCase());
            }

        } catch (SQLException e) {
            log.error("SQLException: {}, VendorError: {}", e.getMessage(), e.getErrorCode());
        }
        godUsers.add(admin);
        godUsers.add(channel.replace("#", ""));
        log.info("Initialized mods list {} | god users {}.", mods, godUsers);
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

    public void addDisabled(String from, String args) {
        String username = Utils.getArg(args, 0);
        if (username == null) {
            username = from;
        }
        if (state.disabledUsers.contains(username.toLowerCase())) {
            return;
        }
        if (mods.contains(from.toLowerCase()) || from.equalsIgnoreCase(username)) {
            log.info(from + " added " + username + " to disabled list");

            try (Connection conn = DriverManager.getConnection(SQLCredentials);
                 PreparedStatement stmt = conn.prepareStatement("CALL chat_stats.sp_add_disabled(?,?);"))
            {

                stmt.setString(1, from);
                stmt.setString(2, username);
                stmt.executeQuery();
                state.disabledUsers.add(username.toLowerCase());
                if (!from.equals("Autoban") && (mods.contains(from.toLowerCase()) ||
                        lastCommandTime.plus(10, ChronoUnit.SECONDS).isBefore(Instant.now())))
                {
                    state.sendingBlockingQueue.add(new Message("@" + from + ", added " + username + " to ignore list."));
                }

            } catch (SQLException e) {
                log.error("SQLException: {}, VendorError: {}", e.getMessage(), e.getErrorCode());
            }
        }
    }

    private void initializeAlts() {
        state.mains = new HashMap<>();
        state.alts = new HashMap<>();
        try (Connection conn = DriverManager.getConnection(SQLCredentials);
             PreparedStatement stmt = conn.prepareStatement("call chat_stats.sp_get_alts()"))
        {
            stmt.execute();
            ResultSet rs = stmt.getResultSet();
            while (rs.next()) {
                String main = rs.getString("main").toLowerCase();
                String alt = rs.getString("alt").toLowerCase();
                state.mains.put(alt, main);
                if (state.alts.containsKey(main)) {
                    List<String> list = state.alts.get(main);
                    list.add(alt);
                    state.alts.put(main, list);
                } else {
                    state.mains.put(main, main);
                    List<String> list = new ArrayList<>();
                    list.add(alt);
                    state.alts.put(main, list);
                }
            }
            log.info("Initialized alts list. {} users with alts.", state.alts.size());

        } catch (SQLException e) {
            log.error("SQLException: {}, VendorError: {}", e.getMessage(), e.getErrorCode());
        }
    }

    private void addAlt(String from, String args) {
        from = from.toLowerCase();
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
        if (mods.contains(from.toLowerCase())) {
            log.info(from + " added " + alt + " to " + main + "'s alt list");

            try (Connection conn = DriverManager.getConnection(SQLCredentials);
                 PreparedStatement stmt = conn.prepareStatement("CALL chat_stats.sp_add_alt(?,?);"))
            {
                stmt.setString(1, main);
                stmt.setString(2, alt);
                stmt.executeQuery();
                state.mains.put(alt, main);
                state.mains.put(main, main);
                List<String> list;
                if (state.alts.containsKey(main)) {
                    list = state.alts.get(main);
                } else {
                    list = new ArrayList<>();
                }
                list.add(alt);
                state.alts.put(main, list);
                state.sendingBlockingQueue.add(new Message("@" + from + ", added " + alt + " as " + main + "'s alt account."));

            } catch (SQLException e) {
                log.error("SQLException: {}, VendorError: {}", e.getMessage(), e.getErrorCode());
            }
        }
    }

    private boolean isNotAllowed(String from, String argStr, Command command) {
        if (banned.containsKey(from.toLowerCase())) {
            if (banned.get(from).plus(600, ChronoUnit.SECONDS).isAfter(Instant.now())) {
                log.info("Banned user " + from + " attempted to use a command.");
                return true;
            } else {
                banned.remove(from);
            }
        }

        if (superbanned.containsKey(from.toLowerCase())) {
            if (superbanned.get(from).plus(3600, ChronoUnit.SECONDS).isAfter(Instant.now())) {
                log.info("superbanned user " + from + " attempted to use a command.");
                return true;
            } else {
                superbanned.remove(from);
            }
        }

        if (state.online.get() && !godUsers.contains(from.toLowerCase())) {
            log.info("Attempted to use " + command + " while stream is online");
            return true;
        }

        //10 second cooldown
        if (lastCommandTime.plus(10, ChronoUnit.SECONDS).isAfter(Instant.now()) && !godUsers.contains(from)) {
            return true;
        }

        if (checkOneManSpam(from)) {
            banned.put(from, Instant.now());
            state.sendingBlockingQueue.add(new Message("@" + from + ", stop one man spamming. Banned from using commands for 10 minutes peepoD"));
            return true;
        }

        String username = Utils.getArg(argStr, 0);

        if (username == null) {
            username = from;
        }
        username = Utils.cleanName(from, username);

        if (isBot(from, username)) {
            return true;
        }

        if (state.disabledUsers.contains(username.toLowerCase()) && (command == RQ || command == RS || command == LASTMESSAGE
                || command == FIRSTMESSAGE))
        {
            state.sendingBlockingQueue.add(
                    new Message("@" + from + ", that user has been removed from the "
                            + command
                            + " command (either by their own wish or by a mod). Type !adddisabled to remove yourself or !remdisabled to re-enable commands."));
            lastCommandTime = Instant.now();
            return true;
        }

        if (command == LASTMESSAGE && username.equalsIgnoreCase(from)) {
            state.sendingBlockingQueue.add(new Message("PepeSpin"));
            return true;
        }

        return false;
    }

    private void removeDisabled(String from, String args) {
        String username = Utils.getArg(args, 0);
        if (username == null) {
            username = from;
        }
        if (!state.disabledUsers.contains(username.toLowerCase())) {
            return;
        }
        if (mods.contains(from.toLowerCase()) || from.equalsIgnoreCase(username)) {
            log.info(from + " removed " + username + " from disabled list");
            try (Connection conn = DriverManager.getConnection(SQLCredentials);
                 PreparedStatement stmt = conn.prepareStatement("CALL chat_stats.sp_remove_disabled(?);"))
            {
                stmt.setString(1, username);
                stmt.executeQuery();
                if (mods.contains(from.toLowerCase()) || lastCommandTime.plus(10, ChronoUnit.SECONDS).isBefore(Instant.now())) {
                    state.sendingBlockingQueue.add(new Message("@" + from + ", removed " + username + " from ignore list."));
                }
                state.disabledUsers.remove(username.toLowerCase());
            } catch (SQLException e) {
                log.error("SQLException: {}, VendorError: {}", e.getMessage(), e.getErrorCode());
            } finally {
                lastCommandTime = Instant.now();
            }
        }
    }

    private void randomQuote(String from, String args) {
        String username = Utils.getArg(args, 0);
        if (username == null) {
            username = from;
        }
        username = Utils.cleanName(from, username);

        String year = Utils.getYear(Utils.getArg(args, 1));

        int count = getCount(username);
        if (count == 0) {
            log.info("Did not find any messages for user " + username);
            state.sendingBlockingQueue.add(new Message("@" + from + ", no messages found PEEPERS"));
            return;
        }
        String fullNameStr = "";

        try (SolrClient solr = new HttpSolrClient.Builder(solrCredentials).build()) {
            SolrQuery query = new SolrQuery();
            fullNameStr = state.getAltsSolrString(username);
            query.set("q", fullNameStr);
            if (year != null) {
                query.set("fq", "-message:\"!rq\" AND -message:\"!chain\" AND -message:\"!lastmessage\" AND time:" + year);
            } else {
                query.set("fq", "-message:\"!rq\" AND -message:\"!chain\" AND -message:\"!lastmessage\"");
            }
            int seed = ThreadLocalRandom.current().nextInt(0, 9999999);
            query.set("sort", "random_" + seed + " asc");
            int amount = 50;
            query.set("rows", amount);
            QueryResponse response = solr.query(query);
            SolrDocumentList results = response.getResults();

            if (results.getNumFound() == 0) {
                log.info("Did not find any messages for " + fullNameStr);
                state.sendingBlockingQueue.add(new Message("@" + from + ", no messages found PEEPERS"));
                return;
            }
            SolrDocument result = results.get(0);
            String message = (String) result.getFirstValue("message");
            Date date = (Date) result.getFirstValue("time");
            username = (String) result.getFirstValue("username");
            String dateStr = ("[" + date.toInstant().toString().replaceAll("T", " ").replaceAll("Z", "]"));
            String finalMessage = String.format("%s %s: %s",
                    dateStr,
                    username,
                    message);
            state.sendingBlockingQueue.add(new Message(finalMessage));
        } catch (IOException | SolrServerException e) {
            log.error("Solr error: " + e.getMessage());
        }
    }

    List<Instant> times = new ArrayList<>();
    String previousMessage = "";

    private boolean checkOneManSpam(String from) {
        if (!previousMessage.equals(from)) {
            previousMessage = from;
            times.clear();
        }
        times.add(Instant.now());
        times.removeIf(instant -> instant.plus(300, ChronoUnit.SECONDS).isAfter(Instant.now()));
        return times.size() >= 5;
    }

    private void getFollowList(String args, String from) {
        String username = Utils.getArg(args, 0);
        if (username == null) {
            username = from;
        }
        username = Utils.cleanName(from, username);
        String text = apiHandler.getFollowList(username);
        String link = "stalk_" + username;
        if (text == null) {
            log.info("No follow list found for " + username);
            state.sendingBlockingQueue.add(new Message("@" + from + ", no such user found PEEPERS"));
            return;
        }

        String finalUsername = username;
        new Thread(() -> {
            try {
                FtpHandler ftpHandler = new FtpHandler();
                if (ftpHandler.upload(link, text)) {
                    String output = String.format("@%s all channels followed by %s: %s%s", from, finalUsername, website, link);
                    log.info(output);
                    state.sendingBlockingQueue.add(new Message(output));
                }
            } catch (IOException e) {
                log.error("Error writing to outputstream: " + e.getMessage());
            } catch (NullPointerException e) {
                log.error("Error uploading logs to ftp");
            }
        }).start();
    }

    private void getLogs(String args, String from) {
        String username = Utils.getArg(args, 0);
        if (username == null) {
            return;
        }
        username = Utils.cleanName(from, username);

        logCache.putIfAbsent(username, 0);
        int count = getCount(username);
        if (count == 0) {
            log.info("Did not find any logs for user " + username);
            state.sendingBlockingQueue.add(new Message("@" + from + ", no messages found PEEPERS"));
            return;
        }
        String link = username;
        if (count == logCache.get(username)) {
            String output = String.format("@%s logs for %s: %s%s", from, username, website, link);
            log.info("No change to message count, not updating link.");
            state.sendingBlockingQueue.add(new Message(output));
            return;
        } else {
            logCache.put(username, count);
        }

        Instant startTime = Instant.now();
        try (Connection conn = DriverManager.getConnection(SQLCredentials)) {
            PreparedStatement stmt;
            if (username.equalsIgnoreCase("all")) {
                stmt = conn.prepareStatement("CALL chat_stats.sp_get_logs();");
            } else {
                stmt = conn.prepareStatement("CALL chat_stats.sp_get_user_logs(?);");
                stmt.setString(1, username);
            }
            ResultSet rs = stmt.executeQuery();
            Instant queryEndTime = Instant.now();
            log.info("Query took: " + (Utils.convertTime((int) (queryEndTime.minus(startTime.toEpochMilli(), ChronoUnit.MILLIS).toEpochMilli() / 1000))));
            StringBuilder sb = new StringBuilder();
            sb.append("<!DOCTYPE html>");
            sb.append("\r\n");
            sb.append("<html>");
            sb.append("\r\n");
            sb.append("<head>");
            sb.append("\r\n");
            sb.append("<meta charset=\"utf-8\">");
            sb.append("\r\n");
            sb.append("<title>");
            sb.append("Logs for ");
            sb.append(username);
            sb.append("</title>");
            sb.append("\r\n");
            sb.append("<!-- if you read this you are gat robDab -->");
            sb.append("\r\n");
            sb.append("</head>");
            sb.append("\r\n");
            sb.append("<body>");
            sb.append("=========================================================");
            sb.append("<br>\r\n");
            sb.append("Logs for ");
            sb.append(username);
            sb.append(" in the channel ");
            sb.append(channel);
            sb.append("<br>\n");
            int currentCount;
            if (rs.last()) {
                currentCount = rs.getRow();
                sb.append(currentCount);
                sb.append(" messages listed out of ");
                sb.append(count);
                sb.append(" total.");
                rs.beforeFirst();
            }
            java.util.Date date = new Date();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            sb.append("<br>\n");
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            sb.append("Generated ");
            sb.append(dateFormat.format(date));
            sb.append(". All times UTC.");
            sb.append("<br>\n");
            sb.append("=========================================================");
            sb.append("<br>\n");
            sb.append("<br>\n");
            while (rs.next()) {
                String time = rs.getTimestamp("time").toString();
                time = "[" + time.substring(0, time.length() - 2) + "]";
                sb.append(time);
                sb.append(" ");
                sb.append(new String(rs.getBytes("username"), StandardCharsets.UTF_8));
                sb.append(": ");
                String message = new String(rs.getBytes("message"), StandardCharsets.UTF_8).
                        replaceAll("&", "&amp;").
                        replaceAll("<", "&lt;").
                        replaceAll(">", "&gt;");
                sb.append(message);
                sb.append("<br>\n");
            }
            sb.append("<br>\n");
            sb.append("END OF FILE");
            sb.append("<br>\n");
            sb.append("</body>");
            sb.append("\r\n");
            sb.append("</html>");
            sb.append("\r\n");
            log.info("Data compilation took: " + (Utils.convertTime((int) (Instant.now().minus(queryEndTime.toEpochMilli(), ChronoUnit.MILLIS).toEpochMilli() / 1000))));

            String finalUsername = username;
            new Thread(() -> {
                try {
                    FtpHandler ftpHandler = new FtpHandler();
                    if (ftpHandler.upload(link, sb.toString())) {
                        String output = String.format("@%s logs for %s: %s%s", from, finalUsername, website, link);
                        log.info(output);
                        log.info("Total time: " + (Utils.convertTime((int) (Instant.now().minus(startTime.toEpochMilli(), ChronoUnit.MILLIS).toEpochMilli() / 1000))));
                        state.sendingBlockingQueue.add(new Message(output));
                    }
                } catch (IOException e) {
                    log.error("Error writing to outputstream: " + e.getMessage());
                } catch (NullPointerException e) {
                    log.error("Error uploading logs to ftp");
                }
            }).start();
        } catch (SQLException e) {
            log.error("SQLException: {}, VendorError: {}", e.getMessage(), e.getErrorCode());
        }
    }
}