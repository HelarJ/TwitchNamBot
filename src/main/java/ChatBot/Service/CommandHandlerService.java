package ChatBot.Service;

import ChatBot.Dataclass.Message;
import ChatBot.StaticUtils.Config;
import ChatBot.StaticUtils.Running;
import ChatBot.StaticUtils.SharedQueues;
import ChatBot.StaticUtils.Utils;
import ChatBot.dao.ApiHandler;
import ChatBot.dao.FtpHandler;
import ChatBot.dao.SQLHandler;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
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
import java.util.TimeZone;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

public class CommandHandlerService extends AbstractExecutionThreadService {
    private final Logger logger = Logger.getLogger(CommandHandlerService.class.toString());
    private final String solrCredentials = Config.getSolrCredentials();
    private Instant lastCommandTime = Instant.now().minus(30, ChronoUnit.SECONDS);
    private Instant lastLogTime = Instant.now().minus(30, ChronoUnit.SECONDS);
    private final String SQLCredentials = Config.getSQLCredentials();
    private final HashMap<String, Integer> logCache = new HashMap<>();
    private final HashMap<String, Integer> spammers = new HashMap<>();
    private final HashMap<String, Instant> banned = new HashMap<>();
    private final HashMap<String, Instant> superbanned = new HashMap<>();
    private final String website = Config.getBotWebsite();
    private final String botName = Config.getTwitchUsername();
    private List<String> godUsers;
    private HashMap<String, List<String>> alts;
    private HashMap<String, String> mains;
    private List<String> disabled;
    private List<String> mods;
    private final String admin = Config.getBotAdmin();
    private final String channel = Config.getChannelToJoin();
    private final SQLHandler sqlHandler = new SQLHandler();
    private final ApiHandler apiHandler = new ApiHandler();

    public CommandHandlerService() {
        FtpHandler ftpHandler = new FtpHandler();
        ftpHandler.cleanLogs();

        refreshLists("Startup");
    }

    @Override
    protected void shutDown() {
        logger.info(CommandHandlerService.class + " stopped.");
    }

    @Override
    protected void startUp() {
        logger.info(CommandHandlerService.class + " started.");
    }

    public void refreshLists(String from) {
        if (from.equals("Startup") || mods.contains(from)) {
            initializeMods();
            initializeDisabled();
            initializeAlts();
            initializeBlacklist();
            if (!from.equals("Startup")) {
                SharedQueues.sendingBlockingQueue.add(new Message("Lists refreshed HACKERMANS"));
            }
        }
    }

    @Override
    public void run() throws InterruptedException {
        while (Running.isBotStillRunning()) {
            Message message = SharedQueues.messageBlockingQueue.take();
            if (message.isPoison()) {
                logger.info(CommandHandlerService.class + " poisoned.");
                break;
            }
            handleCommand(message);
        }
    }

    public void handleCommand(Message cmd) {
        String name = cmd.getSender();
        String cmdName = cmd.getName();

        if (cmdName == null) {
            return;
        }
        String argStr = cmd.getArguments();
        switch (cmdName) {
            case "nammers" -> sendTop10to();
            case "namping" -> ping(name);
            case "namban" -> ban(name, argStr);
            case "names" -> names(name, argStr);
            case "namrefresh" -> refreshLists(name);
            case "namcommands" -> namCommands(name);
            case "namchoose" -> choose(argStr, name);
            case "nam" -> userNam(name, argStr);
            case "lastmessage" -> lastMessage(name, argStr);
            case "firstmessage" -> firstMessage(name, argStr);
            case "log", "logs" -> getLogs(argStr, name);
            case "rq" -> randomQuote(name, argStr);
            case "rs" -> randomSearch(name, argStr);
            case "adddisabled" -> addDisabled(name, argStr);
            case "remdisabled" -> removeDisabled(name, argStr);
            case "fs" -> firstOccurrence(name, argStr);
            case "search" -> search(name, argStr);
            case "searchuser" -> searchUser(name, argStr);
            case "addalt" -> addAlt(name, argStr);
            case "stalklist" -> getFollowList(argStr, name);
            default -> {
                return;
            }
        }
        logCommandUse(name, cmdName, argStr);

    }

    private void logCommandUse(String from, String command, String arguments) {
        logger.info(String.format("%s used %s with arguments [%s].", from, command, arguments));
    }

    private void namCommands(String name) {
        if (isNotAllowed(name, name, "commands")) {
            return;
        }
        SharedQueues.sendingBlockingQueue.add(new Message("@" + name + ", commands for this bot: " + website.substring(0, website.length() - 5) + "/commands"));

        lastCommandTime = Instant.now();
    }

    private void ban(String from, String args) {
        if (godUsers.contains(from.toLowerCase())) {
            String username = Utils.getArg(args, 0);
            if (username == null) {
                return;
            }
            username = Utils.cleanName(from, args);

            superbanned.put(username, Instant.now());
            SharedQueues.sendingBlockingQueue.add(new Message("Banned " + username + " from using the bot for 1h."));
        }
    }

    private void names(String from, String args) {
        String username = Utils.getArg(args, 0);
        if (username == null) {
            return;
        }

        username = Utils.cleanName(from, username);

        if (isNotAllowed(from, username, "names")) {
            return;
        }
        try (Connection conn = DriverManager.getConnection(SQLCredentials);
             PreparedStatement stmt = conn.prepareStatement("call chat_stats.sp_get_names(?)"))
        {
            stmt.setString(1, username.toLowerCase());
            ResultSet rs = stmt.executeQuery();
            StringBuilder names = new StringBuilder("@");
            names.append(from);
            names.append(", ");
            names.append(username);
            names.append("'s other names are: ");
            int amount = 0;
            while (rs.next()) {
                String name = rs.getString("username");
                if (!name.equalsIgnoreCase(username)) {
                    amount++;
                    names.append(name);
                    names.append(", ");
                }
            }
            if (amount >= 1) {
                names.reverse();
                names.deleteCharAt(0);
                names.deleteCharAt(0);
                names.reverse();
                SharedQueues.sendingBlockingQueue.add(new Message(names.toString()));
            } else if (amount == 0) {
                SharedQueues.sendingBlockingQueue.add(new Message("@" + from + ", no alternate names found in logs PEEPERS"));
            }
        } catch (SQLException e) {
            logger.severe("SQL ERROR: " + "SQLException: " + e.getMessage() + ", VendorError: " + e.getErrorCode());
        } finally {
            lastCommandTime = Instant.now();
        }

    }

    private void choose(String choiceMsg, String from) {
        if (isNotAllowed(from, from, "choose")) {
            return;
        }
        choiceMsg = choiceMsg.replaceAll(" \uDB40\uDC00", "");
        String[] choices = choiceMsg.split(" ");
        if (choices.length == 0) {
            return;
        }
        int choice = ThreadLocalRandom.current().nextInt(0, choices.length);
        SharedQueues.sendingBlockingQueue.add(new Message(String.format("@%s, I choose %s", from, choices[choice])));
        lastCommandTime = Instant.now();
    }

    private void ping(String from) {
        if (isNotAllowed(from, from, "ping")) {
            return;
        }
        SharedQueues.sendingBlockingQueue.add(new Message(
                String.format("NamBot online for %s | %d messages sent | %d messages logged | %d timeouts logged, of which %d were permabans.",
                        (Utils.convertTime((int) (Instant.now().minus(Running.getStartTime().toEpochMilli(), ChronoUnit.MILLIS).toEpochMilli() / 1000))),
                        Running.getCommandCount(),
                        Running.getMessageCount(),
                        Running.getTimeoutCount(),
                        Running.getPermabanCount()
                )));
    }

    private String getYear(String year) {
        try {
            if (Integer.parseInt(year) > 2000) {
                return "[" + year + "-01-01T00:00:00Z TO " + year + "-12-31T23:59:59Z]";
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private void searchUser(String from, String msg) {
        String username = Utils.getArg(msg, 0);

        if (username == null) {
            return;
        }
        if (isNotAllowed(from, username, "searchuser")) {
            return;
        }

        try {
            msg = msg.substring(username.length() + 1);

        } catch (StringIndexOutOfBoundsException e) {
            msg = "";
        }
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
                finalMessage = "@" + from + ", " + Utils.addZws(username) + " has used " + Utils.getWordList(msg) + " in " + rowcount + " messages.";
                SolrDocument result1 = response.getResults().get(0);
                /*
                long wordcount = (long) result1.get("ttf(message,"+phrase+")");
                if (wordcount > 0){
                    finalMessage = "@"+from+", "+username.charAt(0)+zws1+zws2+username.substring(1)+" has used "+phrase+" in "+rowcount+" messages. Total count: "+wordcount+".";
                }*/
            } catch (IndexOutOfBoundsException ignored) {
            }
            SharedQueues.sendingBlockingQueue.add(new Message(finalMessage));

        } catch (IOException | SolrServerException | BaseHttpSolrClient.RemoteSolrException e) {
            logger.warning(e.getMessage());
        } finally {
            lastCommandTime = Instant.now();
        }
    }

    private void search(String from, String msg) {
        if (isNotAllowed(from, "nouser", "search")) {
            return;
        }
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
            SharedQueues.sendingBlockingQueue.add(new Message(finalMessage));

        } catch (IOException | BaseHttpSolrClient.RemoteSolrException | SolrServerException e) {
            logger.warning(e.getMessage());
        } finally {
            lastCommandTime = Instant.now();
        }
    }

    private void firstOccurrence(String from, String msg) {
        if (isNotAllowed(from, "nouser", "first")) {
            return;
        }
        if (!godUsers.contains(from)) {
            return;
        }
        String phrase = Utils.getSolrPattern(msg);

        try (SolrClient solr = new HttpSolrClient.Builder(solrCredentials).build()) {
            SolrQuery query = new SolrQuery();
            query.set("q", phrase + " AND -message:\"!rs\" AND -message:\"!searchuser\" AND -message:\"!search\" AND -message:\"!rq\"");
            query.set("sort", "time asc");
            query.set("rows", 1);
            QueryResponse response = solr.query(query);
            String finalMessage = "@" + from + ", no messages found PEEPERS";
            try {
                SolrDocument result = response.getResults().get(0);
                String message = (String) result.getFirstValue("message");
                String msgName = (String) result.getFirstValue("username");
                if (disabled.contains(msgName.toLowerCase())) {
                    msgName = "<redacted>";
                }
                Date date = (Date) result.getFirstValue("time");
                String dateStr = ("[" + date.toInstant().toString().replaceAll("T", " ").replaceAll("Z", "]"));
                finalMessage = String.format("@%s, first occurrence: %s %s: %s", from, dateStr, Utils.addZws(msgName), message);
            } catch (IndexOutOfBoundsException ignored) {
            }
            SharedQueues.sendingBlockingQueue.add(new Message(finalMessage));

        } catch (IOException | BaseHttpSolrClient.RemoteSolrException | SolrServerException e) {
            logger.warning(e.getMessage());
        } finally {
            lastCommandTime = Instant.now();
        }
    }

    private void randomSearch(String from, String msg) {
        String username = Utils.getArg(msg, 0);
        if (username == null || username.length() == 0) {
            return;
        }
        username = Utils.cleanName(from, username);
        if (!godUsers.contains(from) && !username.equalsIgnoreCase(from)) {
            return;
        }
        if (isNotAllowed(from, username, "rs")) {
            return;
        }
        try {
            if (msg.startsWith("me ".toLowerCase())) {
                msg = msg.substring(3);
            } else {
                msg = msg.substring(username.length() + 1);
            }
        } catch (StringIndexOutOfBoundsException e) {
            msg = "";
        }

        if (isBot(from, username)) {
            return;
        }
        try (SolrClient solr = new HttpSolrClient.Builder(solrCredentials).build()) {
            SolrQuery query = new SolrQuery();
            String fullNameStr = getAlts(username);
            query.set("q", fullNameStr);
            query.set("fq", Utils.getSolrPattern(msg) + " AND -message:\"!rs\" AND -message:\"!searchuser\" AND -message:\"!search\" AND -message:\"!rq\"");
            int seed = ThreadLocalRandom.current().nextInt(0, 999999999);
            query.set("sort", "random_" + seed + " asc");
            query.set("rows", 1);
            QueryResponse response = solr.query(query);
            String finalMessage = "@" + from + ", no messages found PEEPERS";
            try {
                SolrDocument result = response.getResults().get(0);
                String message = (String) result.getFirstValue("message");
                String msgName = (String) result.getFirstValue("username");
                Date date = (Date) result.getFirstValue("time");
                String dateStr = ("[" + date.toInstant().toString().replaceAll("T", " ").replaceAll("Z", "]"));
                finalMessage = String.format("%s %s: %s", dateStr, Utils.addZws(msgName), message);
            } catch (IndexOutOfBoundsException ignored) {
            }
            SharedQueues.sendingBlockingQueue.add(new Message(finalMessage));

        } catch (IOException | BaseHttpSolrClient.RemoteSolrException | SolrServerException e) {
            logger.warning(e.getMessage());
        } finally {
            lastCommandTime = Instant.now();
        }
    }

    private boolean isBot(String from, String username) {
        if (username.toLowerCase().equals(botName)) {
            SharedQueues.sendingBlockingQueue.add(new Message("PepeSpin"));
            return true;
        }

        lastCommandTime = Instant.now();
        if (!godUsers.contains(from) && (username.contains("*") || username.contains("?") || username.contains("~") || username.contains("{")
                || username.contains("[")))
        {
            SharedQueues.sendingBlockingQueue.add(new Message("@" + from + ", no wildcards allowed NOPERS"));
            return true;
        }
        return false;
    }

    public void userNam(String from, String args) {
        if (Running.online) {
            logger.info("Attempted to use !NaM while stream is online");
            return;
        }
        if (lastCommandTime.plus(2, ChronoUnit.SECONDS).isAfter(Instant.now())) {
            logger.info("Attempted to use !NaM before cooldown was over");
            return;
        }

        String username = Utils.getArg(args, 0);
        if (username == null) {
            username = from;
        }
        int timeout = sqlHandler.getTimeoutAmount(username);
        if (timeout > 0) {
            if (from.equals(username)) {
                SharedQueues.sendingBlockingQueue.add(new Message(String.format("%s, you have spent %s in the shadow realm.", username, Utils.convertTime(timeout))));
            } else {
                SharedQueues.sendingBlockingQueue.add(new Message(String.format("%s has spent %s in the shadow realm.", Utils.addZws(username), Utils.convertTime(timeout))));
            }

        }
        lastCommandTime = Instant.now();
    }

    public void firstMessage(String from, String args) {
        String username = Utils.getArg(args, 0);
        if (username == null) {
            username = from;
        }
        if (isNotAllowed(from, username, "firstmessage")) {
            return;
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
                        Utils.addZws(username),
                        convertedTime,
                        message);
                SharedQueues.sendingBlockingQueue.add(new Message(finalMessage));
            }

        } catch (SQLException e) {
            logger.severe("SQL ERROR: " + "SQLException: " + e.getMessage() + ", VendorError: " + e.getErrorCode());
        } finally {
            lastCommandTime = Instant.now();
        }
    }

    public void lastMessage(String from, String args) {
        String username = Utils.getArg(args, 0);
        if (username == null) {
            return;
        }
        username = Utils.cleanName(from, username);

        if (isNotAllowed(from, username, "lastmessage")) {
            return;
        }

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
                        Utils.addZws(username),
                        convertedTime,
                        message);
                SharedQueues.sendingBlockingQueue.add(new Message(finalMessage));
            }

        } catch (SQLException e) {
            logger.severe("SQL ERROR: " + "SQLException: " +
                    e.getMessage() + ", VendorError: " + e.getErrorCode());
        } finally {
            lastCommandTime = Instant.now();
        }
    }

    private boolean hasNoMessages(String from, String username) {
        int count = getCount(username);
        if (count == 0) {
            logger.info("Did not find any messages for user " + username);
            SharedQueues.sendingBlockingQueue.add(new Message("@" + from + ", no messages found PEEPERS"));
            lastCommandTime = Instant.now();
            lastLogTime = Instant.now();
            return true;
        }
        return false;
    }

    private void sendTop10to() {
        if (Running.online) {
            logger.info("Attempted to use !nammers while stream is online");
            return;
        }
        if (lastCommandTime.plus(10, ChronoUnit.SECONDS).isAfter(Instant.now())) {
            logger.info("Attempted to use !nammers before cooldown was over");
            return;
        }

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
            SharedQueues.sendingBlockingQueue.add(new Message(sb.toString()));
        } catch (SQLException e) {
            logger.severe("SQL ERROR: " + "SQLException: " +
                    e.getMessage() + ", VendorError: " + e.getErrorCode());
        } finally {
            lastCommandTime = Instant.now();
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
            logger.severe("SQL ERROR: " + "SQLException: " +
                    e.getMessage() + ", VendorError: " + e.getErrorCode());
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
            logger.severe("SQL ERROR: " + "SQLException: " +
                    e.getMessage() + ", VendorError: " + e.getErrorCode());
        }
        godUsers.add(admin);
        godUsers.add(channel.replace("#", ""));
        logger.info("Initialized mods list successfully " + mods.size() + " mods | " + godUsers + " god users.");
    }

    private void initializeBlacklist() {
        ArrayList<String> blacklist = new ArrayList<>();
        ArrayList<String> textBlacklist = new ArrayList<>();
        StringBuilder replacelistSb = new StringBuilder();
        try (Connection conn = DriverManager.getConnection(SQLCredentials);
             PreparedStatement stmt = conn.prepareStatement("call chat_stats.sp_get_blacklist()"))
        {
            stmt.execute();
            ResultSet rs = stmt.getResultSet();

            while (rs.next()) {
                String word = rs.getString("word");
                String type = rs.getString("type");
                switch (type) {
                    case "word" -> blacklist.add(word);
                    case "text" -> textBlacklist.add(word);
                    case "replace" -> replacelistSb.append("|").append(word);
                }
            }
            replacelistSb.replace(0, 1, "");
            Running.setBlacklist(blacklist, textBlacklist, replacelistSb.toString());
            logger.info("Blacklist initialized successfully " + blacklist.size() + " blacklisted words.");

        } catch (SQLException e) {
            logger.severe("SQL ERROR: " + "SQLException: " +
                    e.getMessage() + ", VendorError: " + e.getErrorCode());
        }
    }

    private void initializeDisabled() {
        this.disabled = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(SQLCredentials);
             PreparedStatement stmt = conn.prepareStatement("call chat_stats.sp_get_disabled_users()"))
        {
            stmt.execute();
            ResultSet rs = stmt.getResultSet();
            while (rs.next()) {
                String user = rs.getString("username");
                disabled.add(user.toLowerCase());
            }
            logger.info("Initialized disabled list successfully " + disabled.size() + " disabled users.");

        } catch (SQLException e) {
            logger.severe("SQL ERROR: " + "SQLException: " +
                    e.getMessage() + ", VendorError: " + e.getErrorCode());
        }
    }

    public void addDisabled(String from, String args) {
        String username = Utils.getArg(args, 0);
        if (username == null) {
            username = from;
        }
        if (disabled.contains(username.toLowerCase())) {
            return;
        }
        if (mods.contains(from.toLowerCase()) || from.equalsIgnoreCase(username)) {
            logger.info(from + " added " + username + " to disabled list");

            try (Connection conn = DriverManager.getConnection(SQLCredentials);
                 PreparedStatement stmt = conn.prepareStatement("CALL chat_stats.sp_add_disabled(?,?);"))
            {

                stmt.setString(1, from);
                stmt.setString(2, username);
                stmt.executeQuery();
                disabled.add(username.toLowerCase());
                if (!from.equals("Autoban") && (mods.contains(from.toLowerCase()) ||
                        lastCommandTime.plus(10, ChronoUnit.SECONDS).isBefore(Instant.now())))
                {
                    SharedQueues.sendingBlockingQueue.add(new Message("@" + from + ", added " + username + " to ignore list."));
                }

            } catch (SQLException e) {
                logger.severe("SQL ERROR: " + "SQLException: " + e.getMessage() + ", VendorError: " + e.getErrorCode());
            } finally {
                lastCommandTime = Instant.now();
            }
        }
    }

    private void initializeAlts() {
        this.mains = new HashMap<>();
        this.alts = new HashMap<>();
        try (Connection conn = DriverManager.getConnection(SQLCredentials);
             PreparedStatement stmt = conn.prepareStatement("call chat_stats.sp_get_alts()"))
        {
            stmt.execute();
            ResultSet rs = stmt.getResultSet();
            while (rs.next()) {
                String main = rs.getString("main").toLowerCase();
                String alt = rs.getString("alt").toLowerCase();
                mains.put(alt, main);
                if (alts.containsKey(main)) {
                    List<String> list = alts.get(main);
                    list.add(alt);
                    alts.put(main, list);
                } else {
                    mains.put(main, main);
                    List<String> list = new ArrayList<>();
                    list.add(alt);
                    alts.put(main, list);
                }
            }
            logger.info("Initialized alts list successfully " + alts.size() + " users with alts.");

        } catch (SQLException e) {
            logger.severe("SQL ERROR: " + "SQLException: " +
                    e.getMessage() + ", VendorError: " + e.getErrorCode());
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
        if (alts.containsKey(main)) {
            if (alts.get(main).contains(alt)) {
                return;
            }
        }
        if (mods.contains(from.toLowerCase())) {
            logger.info(from + " added " + alt + " to " + main + "'s alt list");

            try (Connection conn = DriverManager.getConnection(SQLCredentials);
                 PreparedStatement stmt = conn.prepareStatement("CALL chat_stats.sp_add_alt(?,?);"))
            {
                stmt.setString(1, main);
                stmt.setString(2, alt);
                stmt.executeQuery();
                mains.put(alt, main);
                mains.put(main, main);
                List<String> list;
                if (alts.containsKey(main)) {
                    list = alts.get(main);
                } else {
                    list = new ArrayList<>();
                }
                list.add(alt);
                alts.put(main, list);
                SharedQueues.sendingBlockingQueue.add(new Message("@" + from + ", added " + alt + " as " + main + "'s alt account."));

            } catch (SQLException e) {
                logger.severe("SQL ERROR: " + "SQLException: " +
                        e.getMessage() + ", VendorError: " + e.getErrorCode());
            } finally {
                lastCommandTime = Instant.now().minus(5, ChronoUnit.SECONDS);
            }
        }
    }

    private boolean isNotAllowed(String from, String username, String cmdName) {
        if (banned.containsKey(from.toLowerCase())) {
            if (banned.get(from).plus(600, ChronoUnit.SECONDS).isAfter(Instant.now())) {
                logger.info("Banned user " + from + " attempted to use a command.");
                return true;
            } else {
                banned.remove(from);
            }
        }
        if (superbanned.containsKey(from.toLowerCase())) {
            if (superbanned.get(from).plus(3600, ChronoUnit.SECONDS).isAfter(Instant.now())) {
                logger.info("superbanned user " + from + " attempted to use a command.");
                return true;
            } else {
                superbanned.remove(from);
            }
        }

        if (Running.online && !godUsers.contains(from.toLowerCase())) {
            logger.info("Attempted to use " + cmdName + " while stream is online");
            return true;
        }

        if (lastCommandTime.plus(10, ChronoUnit.SECONDS).isAfter(Instant.now()) && !godUsers.contains(from.toLowerCase())) {
            logger.info("Attempted to use " + cmdName + " before cooldown was over");
            if (!spammers.containsKey(from)) {
                spammers.put(from, 1);
            } else {
                spammers.put(from, spammers.get(from) + 1);
            }
            if (spammers.get(from) >= 3) {
                banned.put(from, Instant.now());
                SharedQueues.sendingBlockingQueue.add(new Message("@" + from + ", stop spamming. Banned from using commands for 10 minutes peepoD"));
            }
            return true;
        }
        spammers.clear();
        if (checkOneManSpam(from)) {
            banned.put(from, Instant.now());
            SharedQueues.sendingBlockingQueue.add(new Message("@" + from + ", stop one man spamming. Banned from using commands for 10 minutes peepoD"));
            return true;
        }

        if (username.toLowerCase().equals(botName)) {
            SharedQueues.sendingBlockingQueue.add(new Message("PepeSpin"));
            lastCommandTime = Instant.now();
            return true;
        }
        if (disabled.contains(username.toLowerCase()) && (cmdName.equals("rq") || cmdName.equals("rs") || cmdName.equals("lastmessage")
                || cmdName.equalsIgnoreCase("firstmessage")))
        {
            SharedQueues.sendingBlockingQueue.add(
                    new Message("@" + from + ", that user has been removed from the "
                            + cmdName
                            + " command (either by their own wish or by a mod). Type !adddisabled to remove yourself or !remdisabled to re-enable commands."));
            lastCommandTime = Instant.now();
            return true;
        }

        if (cmdName.equals("lastmessage") && username.equalsIgnoreCase(from)) {
            SharedQueues.sendingBlockingQueue.add(new Message("PepeSpin"));
            lastCommandTime = Instant.now();
            return true;
        }

        return false;
    }

    private void removeDisabled(String from, String args) {
        String username = Utils.getArg(args, 0);
        if (username == null) {
            username = from;
        }
        if (!disabled.contains(username.toLowerCase())) {
            return;
        }
        if (mods.contains(from.toLowerCase()) || from.equalsIgnoreCase(username)) {
            logger.info(from + " removed " + username + " from disabled list");
            try (Connection conn = DriverManager.getConnection(SQLCredentials);
                 PreparedStatement stmt = conn.prepareStatement("CALL chat_stats.sp_remove_disabled(?);"))
            {
                stmt.setString(1, username);
                stmt.executeQuery();
                if (mods.contains(from.toLowerCase()) || lastCommandTime.plus(10, ChronoUnit.SECONDS).isBefore(Instant.now())) {
                    SharedQueues.sendingBlockingQueue.add(new Message("@" + from + ", removed " + username + " from ignore list."));
                }
                disabled.remove(username.toLowerCase());
            } catch (SQLException e) {
                logger.severe("SQL ERROR: " + "SQLException: " + e.getMessage() + ", VendorError: " + e.getErrorCode());
            } finally {
                lastCommandTime = Instant.now();
            }
        }
    }

    private String getAlts(String username) {
        username = username.toLowerCase();
        if (!mains.containsKey(username)) {
            return "username:" + username.toLowerCase();
        }
        String main = mains.get(username);
        if (main == null) {
            return "username:" + username.toLowerCase();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("username:");
        sb.append(main);
        if (disabled.contains(main)) {
            return "username:" + username.toLowerCase();
        }

        for (String alt : alts.get(main)) {
            if (!disabled.contains(alt)) {
                sb.append(" OR ");
                sb.append("username:");
                sb.append(alt);
            }
        }
        return sb.toString();
    }

    private void randomQuote(String from, String args) {
        String username = Utils.getArg(args, 0);
        if (username == null) {
            username = from;
        }
        username = Utils.cleanName(from, username);

        if (isNotAllowed(from, username, "rq")) {
            return;
        }
        lastCommandTime = Instant.now();

        String year = getYear(Utils.getArg(args, 1));

        int count = getCount(username);
        if (count == 0) {
            logger.info("Did not find any messages for user " + username);
            SharedQueues.sendingBlockingQueue.add(new Message("@" + from + ", no messages found PEEPERS"));
            lastCommandTime = Instant.now();
            return;
        }
        String fullNameStr = "";

        try (SolrClient solr = new HttpSolrClient.Builder(solrCredentials).build()) {
            SolrQuery query = new SolrQuery();
            fullNameStr = getAlts(username);
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
            SolrDocument result = results.get(0);
            String message = (String) result.getFirstValue("message");
            Date date = (Date) result.getFirstValue("time");
            username = (String) result.getFirstValue("username");
            String dateStr = ("[" + date.toInstant().toString().replaceAll("T", " ").replaceAll("Z", "]"));
            String finalMessage = String.format("%s %s: %s",
                    dateStr,
                    Utils.addZws(username),
                    message);
            SharedQueues.sendingBlockingQueue.add(new Message(finalMessage));

        } catch (IOException | SolrServerException | IndexOutOfBoundsException e) {
            logger.severe("Solr error: " + e.getMessage());
            logger.info("Did not find any messages for " + fullNameStr);
            SharedQueues.sendingBlockingQueue.add(new Message("@" + from + ", no messages found PEEPERS"));
        } finally {
            lastCommandTime = Instant.now();
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
        if (isNotAllowed(from, username, "followlist")) {
            return;
        }
        lastCommandTime = Instant.now();
        String text = apiHandler.getFollowList(username);
        String link = "stalk_" + username;
        if (text == null) {
            logger.info("No follow list found for " + username);
            SharedQueues.sendingBlockingQueue.add(new Message("@" + from + ", no such user found PEEPERS"));
            lastCommandTime = Instant.now();
            return;
        }

        String finalUsername = username;
        new Thread(() -> {
            try {
                FtpHandler ftpHandler = new FtpHandler();
                if (ftpHandler.upload(link, text)) {
                    String output = String.format("@%s all channels followed by %s: %s%s", from, finalUsername, website, link);
                    logger.info(output);
                    SharedQueues.sendingBlockingQueue.add(new Message(output));
                }
            } catch (IOException e) {
                logger.severe("Error writing to outputstream: " + e.getMessage());
            } catch (NullPointerException e) {
                logger.severe("Error uploading logs to ftp");
            }
        }).start();
        lastCommandTime = Instant.now();
    }

    private void getLogs(String args, String from) {
        String username = Utils.getArg(args, 0);
        if (username == null) {
            return;
        }
        username = Utils.cleanName(from, username);
        if (lastLogTime.plus(20, ChronoUnit.SECONDS).isAfter(Instant.now()) &&
                lastCommandTime.plus(5, ChronoUnit.SECONDS).isAfter(Instant.now()) &&
                !godUsers.contains(from.toLowerCase()))
        {
            logger.info(from + " attempted to call !log before cooldown");
            return;
        }
        logCache.putIfAbsent(username, 0);
        int count = getCount(username);
        if (count == 0) {
            logger.info("Did not find any logs for user " + username);
            SharedQueues.sendingBlockingQueue.add(new Message("@" + from + ", no messages found PEEPERS"));
            lastCommandTime = Instant.now();
            lastLogTime = Instant.now();
            return;
        }
        String link = username;
        if (count == logCache.get(username)) {
            String output = String.format("@%s logs for %s: %s%s", from, username, website, link);
            logger.info("No change to message count, not updating link.");
            SharedQueues.sendingBlockingQueue.add(new Message(output));
            lastCommandTime = Instant.now();
            lastLogTime = Instant.now();
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
            logger.info("Query took: " + (Utils.convertTime((int) (queryEndTime.minus(startTime.toEpochMilli(), ChronoUnit.MILLIS).toEpochMilli() / 1000))));
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
            logger.info("Data compilation took: " + (Utils.convertTime((int) (Instant.now().minus(queryEndTime.toEpochMilli(), ChronoUnit.MILLIS).toEpochMilli() / 1000))));

            String finalUsername = username;
            new Thread(() -> {
                try {
                    FtpHandler ftpHandler = new FtpHandler();
                    if (ftpHandler.upload(link, sb.toString())) {
                        String output = String.format("@%s logs for %s: %s%s", from, finalUsername, website, link);
                        logger.info(output);
                        logger.info("Total time: " + (Utils.convertTime((int) (Instant.now().minus(startTime.toEpochMilli(), ChronoUnit.MILLIS).toEpochMilli() / 1000))));
                        SharedQueues.sendingBlockingQueue.add(new Message(output));
                    }
                } catch (IOException e) {
                    logger.severe("Error writing to outputstream: " + e.getMessage());
                } catch (NullPointerException e) {
                    logger.severe("Error uploading logs to ftp");
                }
            }).start();
        } catch (SQLException ex) {
            logger.severe("SQL ERROR: " + "SQLException: " + ex.getMessage() + ", VendorError: " + ex.getErrorCode());
        } finally {
            lastLogTime = Instant.now();
            lastCommandTime = Instant.now();
        }
        lastLogTime = Instant.now();
    }
}
