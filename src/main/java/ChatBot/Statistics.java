package ChatBot;

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
import java.sql.*;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Statistics implements Runnable{
    private final String solrCredentials;
    private boolean online;
    private boolean first = true;
    private final String channel;
    private final TimeoutLogger tl;
    private Instant lastCommandTime;
    private Instant lastLogTime;
    private final String SQLCredentials;
    private final HashMap<String, Integer> logCount;
    private final HashMap<String, Integer> spammers = new HashMap<>();
    private final HashMap<String, Instant> banned = new HashMap<>();
    private final HashMap<String, Instant> superbanned = new HashMap<>();
    private final char zws1 = '\uDB40';
    private final char zws2 = '\uDC00';
    private final String website;
    private final String botName;
    private final BlockingQueue<String> sendingQueue;
    private final BlockingQueue<Command> statisticsQueue;
    private List<String> godUsers;
    private final Thread checkerThread;
    private final ApiHandler apiHandler;
    private final Thread timeoutThread;
    private boolean running = true;
    private final MessageLogger messageLogger;
    private final Thread messageLoggerThread;
    private HashMap<String, List<String>> alts;
    private HashMap<String, String> mains;
    private List<String> disabled;
    private List<String> mods;
    private final Sender sender;
    private final String admin;



    public Statistics(String channel, Sender sender){
        this.sender = sender;
        this.sendingQueue = sender.getSendingQueue();
        this.statisticsQueue = new LinkedBlockingQueue<>();
        this.messageLogger = new MessageLogger();
        this.messageLoggerThread = new Thread(messageLogger, "MessageLogger");
        this.messageLoggerThread.start();
        FtpHandler ftpHandler = new FtpHandler();
        ftpHandler.cleanLogs();
        sender.setLogQueue(messageLogger.getLogQueue());
        lastCommandTime = Instant.now().minus(30, ChronoUnit.SECONDS);
        lastLogTime = Instant.now().minus(30, ChronoUnit.SECONDS);
        this.channel = channel.toLowerCase();
        apiHandler = new ApiHandler(channel, this);
        checkerThread = new Thread(apiHandler, "OnlineChecker");
        checkerThread.start();
        tl = new TimeoutLogger();
        timeoutThread = new Thread(tl, "TimeoutLogger");
        timeoutThread.start();
        Properties p = Running.getProperties();
        this.SQLCredentials = String.format("jdbc:mariadb://%s:%s/%s?user=%s&password=%s", p.getProperty("db.ip"),
                p.getProperty("db.port"), p.getProperty("db.name"), p.getProperty("db.user"), p.getProperty("db.password"));
        this.solrCredentials = String.format("http://%s:%s/solr/%s", p.getProperty("solr.ip"),
                p.getProperty("solr.port"), p.getProperty("solr.core"));
        this.website = p.getProperty("bot.website");
        this.botName = p.getProperty("twitch.nick").toLowerCase();
        this.admin = p.getProperty("bot.admin").toLowerCase();
        this.logCount = new HashMap<>();
        refreshLists("Startup");
    }

    public void refreshLists(String from){
        if (from.equals("Startup") || mods.contains(from)){
            initializeMods();
            initializeDisabled();
            initializeAlts();
            initializeBlacklist();
            if (!from.equals("Startup")){
                sendingQueue.add("Lists refreshed HACKERMANS");
            }
        }
    }

    public void closeThreads(){
        running = false;
        tl.shutdown();
        apiHandler.shutdown();
        messageLogger.shutdown();
        try {
            timeoutThread.join();
            checkerThread.join();
            messageLoggerThread.join();
        } catch (InterruptedException e) {
            Running.getLogger().info("Error closing threads.");
        }
    }

    @Override
    public void run() {
        Running.getLogger().info("Statistics thread started");
        while (Running.getRunning() && running) {
            try {
                Command command = statisticsQueue.poll(3, TimeUnit.SECONDS);
                if (command != null) {
                    handleCommand(command);
                }
            } catch (InterruptedException e) {
                Running.getLogger().warning("Statistics thread interrupted.");
            }
        }

        Running.getLogger().info("Statistics thread stopped");
    }

    public void handleCommand(Command cmd){
        String cmdStr = cmd.getMessage().toLowerCase().replaceAll("\uDB40\uDC00","").stripTrailing();
        String msg = cmd.getMessage();
        String name = cmd.getSender();
        if (cmdStr.startsWith("!nammers")){
            this.sendTop10to();
        } else if (cmdStr.equalsIgnoreCase("!namping")) {
            Running.getLogger().info(String.format("%s used !namping.", name));
            this.ping(name);
        } else if (cmdStr.startsWith("!namban ")) {
            Running.getLogger().info(String.format("%s used !namban.", name));
            this.ban(name, msg.substring(8).split(" ")[0]);
        } else if (cmdStr.startsWith("!names ")) {
            Running.getLogger().info(String.format("%s used !names.", name));
            this.names(name, msg.substring(7).split(" ")[0].replaceAll("@", ""));
        } else if (cmdStr.startsWith("!convert ")) {
            this.convert(name, msg.substring(9));
        } else if (cmdStr.startsWith("!namrefresh")) {
            this.refreshLists(name);
        } else if (cmdStr.startsWith("!namcommands")) {
            this.namCommands(name);
        } else if (cmdStr.startsWith("!namchoose ")) {
            Running.getLogger().info(String.format("%s used !namchoose.", name));
            this.choose(msg.substring(11), name);
        } else if (cmdStr.startsWith("!nam")){
            Running.getLogger().info(String.format("%s used !nam.", name));
            if (cmdStr.equals("!nam")) {
                this.userNam(name, name);
            } else if (cmdStr.startsWith("!nam ")){
                this.userNam(name, msg.substring(5).split(" ")[0]);
            }
        } else if (cmdStr.startsWith("!lastmessage ")) {
            Running.getLogger().info(String.format("%s used !lastmessage.", name));
            this.lastMessage(name, msg.substring(13).split(" ")[0].replaceAll("@", ""));
        } else if (cmdStr.startsWith("!firstmessage ")) {
            Running.getLogger().info(String.format("%s used !firstMessage.", name));
            this.firstMessage(name, msg.substring(14).split(" ")[0].replaceAll("@", ""));
        } else if (cmdStr.startsWith("!log ")) {
            Running.getLogger().info(String.format("%s used !log.", name));
            this.getLogs(msg.substring(5).stripTrailing().split(" ")[0].replaceAll("@", ""), name);
        } else if (cmdStr.startsWith("!rq")) {
            Running.getLogger().info(String.format("%s used !rq.", name));
            if (cmdStr.equals("!rq")) {
                this.randomQuote(name, name, null);
            } else {
                String year = null;
                String username = msg.substring(4).split(" ")[0].replaceAll("@", "");
                int namelen = username.length();
                if (msg.length()>(namelen+8)){
                    year = getYear(msg.substring(namelen+5, namelen+9));
                }
                this.randomQuote(name, username, year);
            }
        } else if (cmdStr.startsWith("!rs ")) {
                Running.getLogger().info(String.format("%s used !rs.", name));
                this.randomSearch(name, msg.substring(4).replaceAll("@", ""));
        } else if (cmdStr.startsWith("!adddisabled")){
            Running.getLogger().info(String.format("%s used !adddisabled.", name));
            if (cmdStr.equals("!adddisabled")){
                this.addDisabled(name, name);
            } else if (cmdStr.startsWith("!adddisabled ")){
                this.addDisabled(name, msg.substring(13).split(" ")[0]);
            }
        } else if (cmdStr.startsWith("!remdisabled")){
            Running.getLogger().info(String.format("%s used !remdisabled.", name));
            if (cmdStr.equals("!remdisabled")){
                this.removeDisabled(name, name);
            } else if (cmdStr.startsWith("!remdisabled ")){
                this.removeDisabled(name, msg.substring(13).split(" ")[0]);
            }
        } else if (cmdStr.startsWith("!search ")) {
            Running.getLogger().info(String.format("%s used !search.", name));
            this.search(name, msg.substring(8));
        } else if (cmdStr.startsWith("!searchuser ")) {
            Running.getLogger().info(String.format("%s used !searchuser.", name));
            this.searchUser(name, msg.substring(12));
        } else if (cmdStr.startsWith("!addalt ")) {
            Running.getLogger().info(String.format("%s used !addalt.", name));
            String[] split = msg.substring(8).split(" ");
            if (split.length>1){
                this.addAlt(name, split[0], split[1]);
            }
        }  else if (cmdStr.startsWith("!stalklist ")) {
            Running.getLogger().info(String.format("%s used !stalklist.", name));
            this.getFollowList(msg.substring(11).split(" ")[0].toLowerCase(), name);
        }
    }

    private void namCommands(String name) {
        if (isNotAllowed(name, name, "commands")){
            return;
        }

        sendingQueue.add("@"+name+", commands for this bot: https://poop.delivery/commands");

        lastCommandTime = Instant.now();
    }

    private void ban(String from, String username){
        if (godUsers.contains(from.toLowerCase())){
            superbanned.put(username, Instant.now());
            sendingQueue.add("Banned "+username+" from using the bot for 1h.");
        }
    }

    private void names(String from, String username){
        if (isNotAllowed(from, username, "names")){
            return;
        }
        try (Connection conn = DriverManager.getConnection(SQLCredentials);
             PreparedStatement stmt = conn.prepareStatement("call chat_stats.sp_get_names(?)")) {
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
                if (!name.equalsIgnoreCase(username)){
                    amount++;
                    names.append(name);
                    names.append(", ");
                }
            }
            if (amount >= 1){
                names = names.reverse();
                names.deleteCharAt(0);
                names.deleteCharAt(0);
                names.reverse();
                sendingQueue.add(names.toString());
            } else if (amount == 0){
                sendingQueue.add("@"+from+", no alternate names found in logs PEEPERS");
            }
        } catch (SQLException e) {
            Running.getLogger().severe("SQL ERROR: " + "SQLException: " + e.getMessage() + ", VendorError: " + e.getErrorCode());
        } finally {
            lastCommandTime = Instant.now();
        }

    }

    private void choose(String choiceMsg, String from){
        if (isNotAllowed(from, from, "choose")){
            return;
        }
        choiceMsg = choiceMsg.replaceAll(" \uDB40\uDC00", "");
        String[] choices = choiceMsg.split(" ");
        int choice = ThreadLocalRandom.current().nextInt(0, choices.length);
        sendingQueue.add(String.format("@%s, I choose %s", from, choices[choice]));
        lastCommandTime = Instant.now();
    }

    private void ping(String from){
        if (isNotAllowed(from, from, "ping")){
            return;
        }
        sendingQueue.add(String.format("NamBot online for %s | %d messages sent | %d messages logged | %d timeouts logged.",
                (convertTime((int) (Instant.now().minus(Running.getStartTime().toEpochMilli(), ChronoUnit.MILLIS).toEpochMilli()/1000))),
                Running.getCommandCount(),
                Running.getMessageCount(),
                Running.getTimeoutCount()
        ));
    }
    private String getYear(String year){
        try {
            if (Integer.parseInt(year)>2000){
                return "["+year+"-01-01T00:00:00Z TO "+year+"-12-31T23:59:59Z]";
            }
        } catch (NumberFormatException ignored){
        }
        return null;
    }


    private void searchUser(String from, String msg) {
        String username = msg.toLowerCase().split(" ")[0];
        if (isNotAllowed(from, username, "searchuser")){
            return;
        }
        try {
            msg = msg.substring(username.length()+1);

        } catch (StringIndexOutOfBoundsException e){
            msg = "";
        }
        String phrase = msg.split(" ")[0];


        if (msg.startsWith("\"")) {
            phrase = msg.substring(1);
            if (phrase.contains("\"")){
                phrase = phrase.substring(0, phrase.indexOf("\""));
            }
        }
        phrase = "\""+phrase+"\"";

        if (username.toLowerCase().equals(botName)){
            sendingQueue.add("PepeSpin");
            return;
        }

        lastCommandTime = Instant.now();
        if (!godUsers.contains(from) && (username.contains("*") || username.contains("?") || username.contains("~") || username.contains("{")
                || username.contains("[") || phrase.contains("{") || phrase.contains("[")
                || phrase.contains("*") || phrase.contains("?") || phrase.contains("~"))){
            sendingQueue.add("@"+from+", no wildcards allowed NOPERS");
            return;
        }

        try (SolrClient solr = new HttpSolrClient.Builder(solrCredentials).build()){
            SolrQuery query = new SolrQuery();
            query.set("q", "message:"+phrase+" AND username:"+username+" AND -username:"+botName+" AND -message:\"!search\" AND -message:\"!searchuser\" AND -message:\"!rs\"");
            query.set("fl", "*,ttf(message,"+phrase+")");
            query.set("rows", 1);
            QueryResponse response = solr.query(query);
            String finalMessage = "@"+from+", no messages found PEEPERS";
            try {
                SolrDocumentList result = response.getResults();
                long rowcount = result.getNumFound();
                finalMessage = "@"+from+", "+username.charAt(0)+zws1+zws2+username.substring(1)+" has used "+phrase+" in "+rowcount+" messages.";
                SolrDocument result1 = response.getResults().get(0);
                long wordcount = (long) result1.get("ttf(message,"+phrase+")");
                if (wordcount > 0){
                    finalMessage = "@"+from+", "+username.charAt(0)+zws1+zws2+username.substring(1)+" has used "+phrase+" in "+rowcount+" messages. Total count: "+wordcount+".";
                }
            } catch (IndexOutOfBoundsException ignored){
            }
            sendingQueue.add(finalMessage);

        } catch (IOException | SolrServerException | BaseHttpSolrClient.RemoteSolrException e) {
            Running.getLogger().warning(e.getMessage());
        } finally {
            lastCommandTime = Instant.now();
        }
    }

    private void search(String from, String msg) {
        if (isNotAllowed(from, "nouser", "search")){
            return;
        }
        String phrase = msg.split(" ")[0];
        if (phrase == null || phrase.length() == 0){
            return;
        }
        if (msg.startsWith("\"")) {
            phrase = msg.substring(1);
            if (phrase.contains("\"")){
                phrase = phrase.substring(0, phrase.indexOf("\""));
            }
        }
        phrase = "\""+phrase+"\"";

        try (SolrClient solr = new HttpSolrClient.Builder(solrCredentials).build()){
            SolrQuery query = new SolrQuery();
            query.set("q", "message:"+phrase+" AND -username:"+botName+" AND -message:\"!search\" AND -message:\"!searchuser\" AND -message:\"!rs\"");
            query.set("fl", "*,ttf(message,"+phrase+")");
            query.set("rows", 1);
            QueryResponse response = solr.query(query);
            String finalMessage = "@"+from+", no messages found PEEPERS";
            try {
                SolrDocumentList result = response.getResults();
                long rowcount = result.getNumFound();
                finalMessage = "@"+from+" found phrase "+phrase+" in "+rowcount+" rows.";
                SolrDocument result1 = response.getResults().get(0);
                long wordcount = (long) result1.get("ttf(message,"+phrase+")");
                if (wordcount > 0){
                    finalMessage = "@"+from+" found word "+phrase+" in "+rowcount+" rows. Total count: "+wordcount+".";
                }
            } catch (IndexOutOfBoundsException ignored){
            }
            sendingQueue.add(finalMessage);

        } catch (IOException | BaseHttpSolrClient.RemoteSolrException | SolrServerException e) {
            Running.getLogger().warning(e.getMessage());
        } finally {
            lastCommandTime = Instant.now();
        }


    }
    private void convert(String msg, String from){
        if (isNotAllowed(from, from, "convert")){
            return;
        }
        String numberStr = msg.replaceAll("[^0-9.]+", "");
        double number;
        if (numberStr.length()>0){
            try {
                number = Double.parseDouble(numberStr);
            } catch (Exception ignored){
                return;
            }
        } else {
            return;
        }

        if (msg.toLowerCase().contains("f")){
            sendingQueue.add(String.format("@%s, %.2fF is %.2fC", from, number, (number-32.0)*0.5556));
        } else if (msg.toLowerCase().contains("c")){
            sendingQueue.add(String.format("@%s, %.2fC is %.2fF%n", from, number, number*1.8+32.0));
        }

        lastCommandTime = Instant.now();
    }

    private void randomSearch(String from, String msg) {
        String username = msg.toLowerCase().split(" ")[0];
        if (username == null || username.length() == 0){
            return;
        }
        if (username.equalsIgnoreCase("me")){
            username = from;
        }
        if (!godUsers.contains(from) && !username.equalsIgnoreCase(from)){
            return;
        }
        if (isNotAllowed(from, username, "rs")){
            return;
        }
        try {
            if (msg.startsWith("me ".toLowerCase())) {
                msg = msg.substring(3);
            } else {
                msg = msg.substring(username.length() + 1);
            }
        } catch (StringIndexOutOfBoundsException e){
            msg = "";
        }
        String phrase = msg.replaceAll(" \uDB40\uDC00", "");
        List<String> phraseList = new ArrayList<>();
        Matcher m = Pattern.compile("([^\"]\\S*|\".+?\")\\s*").matcher(phrase);
        while (m.find())
            phraseList.add(m.group(1));
        StringBuilder sb = new StringBuilder();
        for (String word: phraseList){
            if (word.startsWith("-")){
                sb.append("-message:");
                sb.append(word.replaceAll("-", ""));
            } else {
                sb.append("message:");
                sb.append(word);
            }
            sb.append(" AND ");
        }
        if (sb.length()>5){
            sb.replace(sb.length()-5, sb.length(), "");
        } else {
            sb.append("\"\"");
        }
        System.out.println(sb.toString());


        if (username.toLowerCase().equals(botName)){
            sendingQueue.add("PepeSpin");
            return;
        }
        if (disabled.contains(username.toLowerCase())){
            sendingQueue.add("@"+from+", that user has been removed from the !rs command (either by their own wish or by a mod).");
            lastCommandTime = Instant.now();
            return;
        }

        lastCommandTime = Instant.now();
        if (!godUsers.contains(from) && (username.contains("*") || username.contains("?") || username.contains("~") || username.contains("{")
                || username.contains("["))){
            sendingQueue.add("@"+from+", no wildcards allowed NOPERS");
            return;
        }
        try (SolrClient solr = new HttpSolrClient.Builder(solrCredentials).build()){
            SolrQuery query = new SolrQuery();
            String fullNameStr = getAlts(username);
            query.set("q", fullNameStr);
            query.set("fq", sb.toString() + " AND -message:\"!rs\" AND -message:\"!searchuser\" AND -message:\"!search\" AND -message:\"!rq\"");
            int seed = ThreadLocalRandom.current().nextInt(0,999999999);
            query.set("sort", "random_"+seed+" asc");
            query.set("rows", 1);
            QueryResponse response = solr.query(query);
            String finalMessage = "@"+from+", no messages found PEEPERS";
            try {
                SolrDocument result = response.getResults().get(0);
                String message = (String) result.getFirstValue("message");
                String msgName = (String) result.getFirstValue("username");
                Date date = (Date) result.getFirstValue("time");
                String dateStr = ("["+date.toInstant().toString().replaceAll("T", " ").replaceAll("Z", "]"));
                finalMessage = String.format("%s %s: %s", dateStr, msgName.substring(0,1)+zws1+zws2+msgName.substring(1), message);
            } catch (IndexOutOfBoundsException ignored){
            }
            sendingQueue.add(finalMessage);

        } catch (IOException | BaseHttpSolrClient.RemoteSolrException | SolrServerException e) {
            Running.getLogger().warning(e.getMessage());
        } finally {
            lastCommandTime = Instant.now();
        }
    }

    public void recordTimeout(String username, String userid, int length) {
        if (length == 0){
            tl.addTimeout(username, length);
            return;
        }
        Running.addTimeoutCount();
        try (Connection conn = DriverManager.getConnection(SQLCredentials);
             PreparedStatement stmt = conn.prepareStatement("CALL chat_stats.sp_log_timeout(?,?,?,?);")) {
            stmt.setString(1, username);
            stmt.setString(2, userid);
            stmt.setInt(3, length);
            stmt.setBoolean(4, online);
            stmt.executeQuery();
        } catch (SQLException ex) {
            Running.getLogger().severe("SQL ERROR: " + "SQLException: " + ex.getMessage() + ", VendorError: " + ex.getErrorCode());
        } finally {
            if (!online){
                tl.addTimeout(username, length);
            }
        }
    }

    public void userNam(String from, String username) {
        if (online){
            Running.getLogger().info("Attempted to use !NaM while stream is online");
            return;
        }
        if (lastCommandTime.plus(2, ChronoUnit.SECONDS).isAfter(Instant.now())){
            Running.getLogger().info("Attempted to use !NaM before cooldown was over");
            return;
        }

        try (Connection conn = DriverManager.getConnection(SQLCredentials);
             PreparedStatement stmt = conn.prepareStatement("call chat_stats.sp_get_usernam(?)")) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            rs.next();
            int timeout = rs.getInt("timeout");
            if (timeout > 0) {
                if (from.equals(username)){
                    sendingQueue.add(String.format("%s, you have spent %s in the shadow realm.", username, convertTime(timeout)));
                } else {
                    sendingQueue.add(String.format("%s has spent %s in the shadow realm.", username.substring(0,1)+zws1+zws2+username.substring(1), convertTime(timeout)));
                }

            }

        } catch (SQLException ex) {
            Running.getLogger().warning("SQL ERROR: " + "SQLException: " + ex.getMessage() + ", VendorError: " + ex.getErrorCode());
        } finally {
            lastCommandTime = Instant.now();
        }
    }
    public void firstMessage(String from, String username) {
        if (isNotAllowed(from, username, "firstmessage")){
            return;
        }
        int count = getCount(username);
        if (count == 0){
            Running.getLogger().info("Did not find any messages for user " + username);
            sendingQueue.add("@"+from+", no messages found PEEPERS");
            lastCommandTime = Instant.now();
            lastLogTime = Instant.now();
            return;
        }

        try (Connection conn = DriverManager.getConnection(SQLCredentials);
             PreparedStatement stmt = conn.prepareStatement("call chat_stats.sp_get_first_message(?)")) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String convertedTime = convertTime((int) (Instant.now().minus(3,
                        ChronoUnit.HOURS).minus(rs.getTimestamp("time").getTime(),
                        ChronoUnit.MILLIS).toEpochMilli() /1000));
                String message = rs.getString("message");
                String finalMessage = String.format("%s's first message %s ago was: %s",
                        username.substring(0,1)+zws1+zws2+username.substring(1), convertedTime, message);
                sendingQueue.add(finalMessage);
            }

        } catch (SQLException e) {
            Running.getLogger().severe("SQL ERROR: " + "SQLException: " + e.getMessage() + ", VendorError: " + e.getErrorCode());
        } finally {
            lastCommandTime = Instant.now();
        }
    }

    public void lastMessage(String from, String username) {
        if (isNotAllowed(from, username, "lastmessage")){
            return;
        }
        int count = getCount(username);
        if (count == 0){
            Running.getLogger().info("Did not find any messages for user " + username);
            sendingQueue.add("@"+from+", no messages found PEEPERS");
            lastCommandTime = Instant.now();
            lastLogTime = Instant.now();
            return;
        }

        try (Connection conn = DriverManager.getConnection(SQLCredentials);
             PreparedStatement stmt = conn.prepareStatement("call chat_stats.sp_get_last_message(?)")) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String convertedTime = convertTime((int) (Instant.now().minus(3,
                        ChronoUnit.HOURS).minus(rs.getTimestamp("time").getTime(),
                        ChronoUnit.MILLIS).toEpochMilli() /1000));
                String message = rs.getString("message");
                String finalMessage = String.format("%s's last message %s ago was: %s",
                        username.substring(0,1)+zws1+zws2+username.substring(1), convertedTime, message);
                sendingQueue.add(finalMessage);
            }

        } catch (SQLException e) {
            Running.getLogger().severe("SQL ERROR: " + "SQLException: " +
                    e.getMessage() + ", VendorError: " + e.getErrorCode());
        } finally {
            lastCommandTime = Instant.now();
        }
    }

    public void sendTop10to() {
        if (online){
            Running.getLogger().info("Attempted to use !nammers while stream is online");
            return;
        }
        if (lastCommandTime.plus(10, ChronoUnit.SECONDS).isAfter(Instant.now())){
            Running.getLogger().info("Attempted to use !nammers before cooldown was over");
            return;
        }

        try (Connection conn = DriverManager.getConnection(SQLCredentials);
             Statement stmt = conn.createStatement()) {
            ResultSet results = stmt.executeQuery("call chat_stats.sp_get_top10to()");
            StringBuilder sb = new StringBuilder();
            sb.append("Top NaMmers: ");
            while (results.next()) {
                sb.append(results.getString("username"));
                sb.append(": ");
                sb.append(convertTime(results.getInt("timeout")));
                sb.append(" | ");
                if (sb.length() >= 270) {
                    break;
                }
            }
            sendingQueue.add(sb.toString());
        } catch (SQLException e) {
            Running.getLogger().severe("SQL ERROR: " + "SQLException: " +
                    e.getMessage() + ", VendorError: " + e.getErrorCode());
        } finally {
            lastCommandTime = Instant.now();
        }
    }



    public int getCount(String username){
        int count = 0;
        String stmtStr =  "call chat_stats.sp_get_message_count(?)";
        if (username.toLowerCase().equals("all")){
            stmtStr = "call chat_stats.sp_get_all_count(?)";
        }

        try (Connection conn = DriverManager.getConnection(SQLCredentials);
             PreparedStatement stmt = conn.prepareStatement(stmtStr)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()){
                count = rs.getInt("count");
            }

        } catch (SQLException e) {
            Running.getLogger().severe("SQL ERROR: " + "SQLException: " +
                    e.getMessage() + ", VendorError: " + e.getErrorCode());
        }
        return count;
    }


    private void initializeMods(){
        this.mods = new ArrayList<>();
        this.godUsers = new ArrayList<>();
        mods.add(admin);
        mods.add(channel.replace("#", ""));
        mods.add("autoban");
        try (Connection conn = DriverManager.getConnection(SQLCredentials);
             PreparedStatement stmt = conn.prepareStatement("call chat_stats.sp_get_mods()")) {
            stmt.execute();
            ResultSet rs = stmt.getResultSet();

            while (rs.next()) {
                String name = rs.getString("name");
                mods.add(name.toLowerCase());
            }

        } catch (SQLException e) {
            Running.getLogger().severe("SQL ERROR: " + "SQLException: " +
                    e.getMessage() + ", VendorError: " + e.getErrorCode());
        }
        godUsers.add(admin);
        godUsers.add(channel.replace("#", ""));
        Running.getLogger().info("Initialized mods list successfully "+mods.size()+" mods | "+godUsers+" god users.");
    }
    private void initializeBlacklist(){
        ArrayList<String> blacklist = new ArrayList<>();
        ArrayList<String> textBlacklist = new ArrayList<>();
        StringBuilder replacelistSb = new StringBuilder();
        try (Connection conn = DriverManager.getConnection(SQLCredentials);
             PreparedStatement stmt = conn.prepareStatement("call chat_stats.sp_get_blacklist()")) {
            stmt.execute();
            ResultSet rs = stmt.getResultSet();

            while (rs.next()) {
                String word = rs.getString("word");
                String type = rs.getString("type");
                switch (type) {
                    case "word":
                        blacklist.add(word);
                        break;
                    case "text":
                        textBlacklist.add(word);
                        break;
                    case "replace":
                        replacelistSb.append("|").append(word);
                        break;
                }
            }
            replacelistSb.replace(0,1,"");
            sender.setBlacklist(blacklist, textBlacklist, replacelistSb.toString());
            Running.getLogger().info("Blacklist initialized successfully " + blacklist.size() + " blacklisted words.");

        } catch (SQLException e) {
            Running.getLogger().severe("SQL ERROR: " + "SQLException: " +
                    e.getMessage() + ", VendorError: " + e.getErrorCode());
        }
    }
    private void initializeDisabled(){
        this.disabled = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(SQLCredentials);
             PreparedStatement stmt = conn.prepareStatement("call chat_stats.sp_get_disabled_users()")) {
            stmt.execute();
            ResultSet rs = stmt.getResultSet();
            while (rs.next()) {
                String user = rs.getString("username");
                disabled.add(user.toLowerCase());
            }
            Running.getLogger().info("Initialized disabled list successfully " + disabled.size() + " disabled users.");

        } catch (SQLException e) {
            Running.getLogger().severe("SQL ERROR: " + "SQLException: " +
                    e.getMessage() + ", VendorError: " + e.getErrorCode());
        }
    }
    public void addDisabled(String from, String username){
        if (disabled.contains(username.toLowerCase())){
            return;
        }
        if (mods.contains(from.toLowerCase()) || from.toLowerCase().equals(username.toLowerCase())){
            Running.getLogger().info(from+" added "+username + " to disabled list");

            try (Connection conn = DriverManager.getConnection(SQLCredentials);
                 PreparedStatement stmt = conn.prepareStatement("CALL chat_stats.sp_add_disabled(?,?);")) {

                stmt.setString(1, from);
                stmt.setString(2, username);
                stmt.executeQuery();
                disabled.add(username.toLowerCase());
                if (!from.equals("Autoban") && (mods.contains(from.toLowerCase()) ||
                        lastCommandTime.plus(10, ChronoUnit.SECONDS).isBefore(Instant.now()))) {
                    sendingQueue.add("@" + from + ", added " + username + " to ignore list.");
                }

            } catch (SQLException e) {
                Running.getLogger().severe("SQL ERROR: " + "SQLException: " + e.getMessage() + ", VendorError: " + e.getErrorCode());
            } finally {
                lastCommandTime = Instant.now();
            }
        }
    }

    private void initializeAlts(){
        this.mains = new HashMap<>();
        this.alts = new HashMap<>();
        try (Connection conn = DriverManager.getConnection(SQLCredentials);
             PreparedStatement stmt = conn.prepareStatement("call chat_stats.sp_get_alts()")) {
            stmt.execute();
            ResultSet rs = stmt.getResultSet();
            while (rs.next()) {
                String main = rs.getString("main").toLowerCase();
                String alt = rs.getString("alt").toLowerCase();
                mains.put(alt, main);
                if (alts.containsKey(main)){
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
            Running.getLogger().info("Initialized alts list successfully " + alts.size() + " users with alts.");

        } catch (SQLException e) {
            Running.getLogger().severe("SQL ERROR: " + "SQLException: " +
                    e.getMessage() + ", VendorError: " + e.getErrorCode());
        }
    }

    public void addAlt(String from, String main, String alt){
        from = from.toLowerCase();
        main = main.toLowerCase();
        alt = alt.toLowerCase();
        if (alts.containsKey(main)){
            if (alts.get(main).contains(alt)){
                return;
            }
        }
        if (mods.contains(from.toLowerCase())){
            Running.getLogger().info(from+" added "+alt + " to "+main+"'s alt list");

            try (Connection conn = DriverManager.getConnection(SQLCredentials);
                 PreparedStatement stmt = conn.prepareStatement("CALL chat_stats.sp_add_alt(?,?);")) {
                stmt.setString(1, main);
                stmt.setString(2, alt);
                stmt.executeQuery();
                mains.put(alt, main);
                mains.put(main, main);
                List<String> list;
                if (alts.containsKey(main)){
                    list = alts.get(main);
                } else {
                    list = new ArrayList<>();
                }
                list.add(alt);
                alts.put(main, list);
                sendingQueue.add("@" + from + ", added " + alt + " as "+main+"'s alt account.");

            } catch (SQLException e) {
                Running.getLogger().severe("SQL ERROR: " + "SQLException: " +
                        e.getMessage() + ", VendorError: " + e.getErrorCode());
            } finally {
                lastCommandTime = Instant.now().minus(5, ChronoUnit.SECONDS);
            }
        }
    }


    public boolean isNotAllowed(String from, String username, String cmdName){
        if (banned.containsKey(from)){
            if (banned.get(from).plus(600, ChronoUnit.SECONDS).isAfter(Instant.now())){
                Running.getLogger().info("Banned user "+ from +" attempted to use a command.");
                return true;
            } else {
                banned.remove(from);
            }
        }
        if (superbanned.containsKey(from)){
            if (superbanned.get(from).plus(3600, ChronoUnit.SECONDS).isAfter(Instant.now())){
                Running.getLogger().info("superbanned user "+ from +" attempted to use a command.");
                return true;
            } else {
                superbanned.remove(from);
            }
        }


        if (online && !godUsers.contains(from.toLowerCase())){
            Running.getLogger().info("Attempted to use "+cmdName+" while stream is online");
            return true;
        }

        if (lastCommandTime.plus(10, ChronoUnit.SECONDS).isAfter(Instant.now()) && !godUsers.contains(from.toLowerCase())){
            Running.getLogger().info("Attempted to use "+cmdName+" before cooldown was over");
            if (!spammers.containsKey(from)){
                spammers.put(from, 1);
            } else {
                spammers.put(from, spammers.get(from)+1);
            }
            if (spammers.get(from)>=3){
                banned.put(from, Instant.now());
                sendingQueue.add("@"+from+", stop spamming. Banned from using commands for 10 minutes peepoD");
            }
            return true;
        }
        spammers.clear();
        if (checkOneManSpam(from)){
            banned.put(from, Instant.now());
            sendingQueue.add("@"+from+", stop one man spamming. Banned from using commands for 10 minutes peepoD");
            return true;
        }

        if (username.toLowerCase().equals(botName)){
            sendingQueue.add("PepeSpin");
            return true;
        }
        if (disabled.contains(username.toLowerCase()) && (cmdName.equals("rq") || cmdName.equals("rs") || cmdName.equals("lastmessage")
                || cmdName.equalsIgnoreCase("firstmessage"))){
            sendingQueue.add("@"+from+", that user has been removed from the "+cmdName+" command (either by their own wish or by a mod).");
            lastCommandTime = Instant.now();
            return true;
        }

        return false;
    }

    public void removeDisabled(String from, String username){
        if (!disabled.contains(username.toLowerCase())){
            return;
        }
        if (mods.contains(from.toLowerCase()) || from.toLowerCase().equals(username.toLowerCase())){
            Running.getLogger().info(from+" removed "+username + " from disabled list");
            try (Connection conn = DriverManager.getConnection(SQLCredentials);
                    PreparedStatement stmt = conn.prepareStatement("CALL chat_stats.sp_remove_disabled(?);")) {
                stmt.setString(1, username);
                stmt.executeQuery();
                if (mods.contains(from.toLowerCase()) || lastCommandTime.plus(10, ChronoUnit.SECONDS).isBefore(Instant.now())){
                    sendingQueue.add("@"+from+", removed "+username+" from ignore list.");
                }
                disabled.remove(username.toLowerCase());
            } catch (SQLException e) {
                Running.getLogger().severe("SQL ERROR: " + "SQLException: " + e.getMessage() + ", VendorError: " + e.getErrorCode());
            } finally {
                lastCommandTime = Instant.now();
            }
        }
    }
    private String getAlts(String username){
        username = username.toLowerCase();
        if (!mains.containsKey(username)){
            return "username:"+username.toLowerCase();
        }
        String main = mains.get(username);
        if (main == null){
            return "username:"+username.toLowerCase();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("username:");
        sb.append(main);
        if (disabled.contains(main)){
            return "username:"+username.toLowerCase();
        }
        for (String alt: alts.get(main)){
            if (!disabled.contains(alt)){
                sb.append(" OR ");
                sb.append("username:");
                sb.append(alt);
            }
        }
        return sb.toString();
    }

    public void randomQuote(String from, String username, String year){
        if (isNotAllowed(from, username, "rq")){
            return;
        }
        lastCommandTime = Instant.now();
        int count = getCount(username);
        if (count == 0){
            Running.getLogger().info("Did not find any messages for user " + username);
            sendingQueue.add("@"+from+", no messages found PEEPERS");
            lastCommandTime = Instant.now();
            return;
        }
        String fullNameStr = "";

        try (SolrClient solr = new HttpSolrClient.Builder(solrCredentials).build()){
            SolrQuery query = new SolrQuery();
            fullNameStr = getAlts(username);
            query.set("q", fullNameStr);
            if (year != null){
                query.set("fq", "-message:\"!rq\" AND -message:\"!chain\" AND -message:\"!lastmessage\" AND time:"+year);
            } else {
                query.set("fq", "-message:\"!rq\" AND -message:\"!chain\" AND -message:\"!lastmessage\"");
            }
            int seed = ThreadLocalRandom.current().nextInt(0,9999999);
            query.set("sort", "random_"+seed+" asc");
            int amount = 50;
            query.set("rows", amount);
            QueryResponse response = solr.query(query);
            SolrDocumentList results = response.getResults();
            SolrDocument result = results.get(0);
            String message = (String) result.getFirstValue("message");
            Date date = (Date) result.getFirstValue("time");
            username = (String) result.getFirstValue("username");
            String dateStr = ("["+date.toInstant().toString().replaceAll("T", " ").replaceAll("Z", "]"));
            String finalMessage = String.format("%s %s: %s", dateStr, username.substring(0,1)+zws1+zws2+username.substring(1), message);
            sendingQueue.add(finalMessage);

        } catch (IOException | SolrServerException | IndexOutOfBoundsException e) {
            Running.getLogger().severe("Solr error: "+e.getMessage());
            Running.getLogger().info("Did not find any messages for "+fullNameStr);
            sendingQueue.add("@"+from+", no messages found PEEPERS");
        } finally {
            lastCommandTime = Instant.now();
        }
    }

    List<Instant> times = new ArrayList<>();
    String previousMessage = "";

    public boolean checkOneManSpam(String from){
        if (!previousMessage.equals(from)) {
            previousMessage = from;
            times.clear();
        }
        times.add(Instant.now());
        times.removeIf(instant -> instant.plus(300, ChronoUnit.SECONDS).isAfter(Instant.now()));
        return times.size()>=5;
    }

    public void getFollowList(String username, String from){
        if (isNotAllowed(from, username, "followlist")){
            return;
        }
        lastCommandTime = Instant.now();
        String text = apiHandler.getFollowList(username);
        String link = "stalk_"+username;
        if (text == null){
            Running.getLogger().info("No follow list found for " + username);
            sendingQueue.add("@"+from+", no such user found PEEPERS");
            lastCommandTime = Instant.now();
            return;
        }

        new Thread (() -> {
            try {
                FtpHandler ftpHandler = new FtpHandler();
                if (ftpHandler.upload(link, text)){
                    String output = String.format("@%s all channels followed by %s: %s%s", from, username, website, link);
                    Running.getLogger().info(output);
                    sendingQueue.add(output);
                }
            } catch (IOException e) {
                Running.getLogger().severe("Error writing to outputstream: "+e.getMessage());
            } catch (NullPointerException e){
                Running.getLogger().severe("Error uploading logs to ftp");
            }
        }).start();
        lastCommandTime = Instant.now();
    }

    public void getLogs(String username, String from) {
        username = username.toLowerCase();
        if (lastLogTime.plus(20, ChronoUnit.SECONDS).isAfter(Instant.now()) &&
                lastCommandTime.plus(5, ChronoUnit.SECONDS).isAfter(Instant.now()) &&
                !godUsers.contains(from.toLowerCase())){
            Running.getLogger().info(from+ " attempted to call !log before cooldown");
            return;
        }
        logCount.putIfAbsent(username, 0);
        int count = getCount(username);
        if (count == 0){
            Running.getLogger().info("Did not find any logs for user " + username);
            sendingQueue.add("@"+from+", no messages found PEEPERS");
            lastCommandTime = Instant.now();
            lastLogTime = Instant.now();
            return;
        }
        String link = username;
        if (count == logCount.get(username)){
            String output = String.format("@%s logs for %s: %s%s", from, username, website, link);
            Running.getLogger().info("No change to message count, not updating link.");
            sendingQueue.add(output);
            lastCommandTime = Instant.now();
            lastLogTime = Instant.now();
            return;
        } else {
            logCount.put(username, count);
        }

        Instant startTime = Instant.now();
        try (Connection conn = DriverManager.getConnection(SQLCredentials)) {
            PreparedStatement stmt;
            if (username.toLowerCase().equals("all")) {
                stmt = conn.prepareStatement("CALL chat_stats.sp_get_logs();");
            } else {
                stmt = conn.prepareStatement("CALL chat_stats.sp_get_user_logs(?);");
                stmt.setString(1, username);
            }
            ResultSet rs = stmt.executeQuery();
            Instant queryEndTime = Instant.now();
            Running.getLogger().info("Query took: "+(convertTime((int) (queryEndTime.minus(startTime.toEpochMilli(), ChronoUnit.MILLIS).toEpochMilli()/1000))));
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
            if (rs.last()){
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
            Running.getLogger().info("Data compilation took: "+(convertTime((int) (Instant.now().minus(queryEndTime.toEpochMilli(), ChronoUnit.MILLIS).toEpochMilli()/1000))));

            String finalUsername = username;
            new Thread (() -> {
                try {
                    FtpHandler ftpHandler = new FtpHandler();
                    if (ftpHandler.upload(link, sb.toString())){
                        String output = String.format("@%s logs for %s: %s%s", from, finalUsername, website, link);
                        Running.getLogger().info(output);
                        Running.getLogger().info("Total time: "+(convertTime((int) (Instant.now().minus(startTime.toEpochMilli(), ChronoUnit.MILLIS).toEpochMilli()/1000))));
                        sendingQueue.add(output);
                    }
                } catch (IOException e) {
                    Running.getLogger().severe("Error writing to outputstream: "+e.getMessage());
                } catch (NullPointerException e){
                    Running.getLogger().severe("Error uploading logs to ftp");
                }
            }).start();
        } catch (SQLException ex) {
            Running.getLogger().severe("SQL ERROR: " + "SQLException: " + ex.getMessage() + ", VendorError: " + ex.getErrorCode());
        } finally {
            lastLogTime = Instant.now();
            lastCommandTime = Instant.now();
        }
        lastLogTime = Instant.now();
        }

    public void delegateMessage(String message) {
        if (lastCommandTime.plus(3, ChronoUnit.SECONDS).isBefore(Instant.now())) {
            sendingQueue.add(message);
        }

    }

    public void setOnline() {
        if (!online || first) {
            online = true;
            first = false;
            messageLogger.setOnline();
            Running.getLogger().info("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@" + channel + " is online.@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
        }
    }

    public void setOffline() {
        if (online || first) {
            online = false;
            first = false;
            messageLogger.setOffline();
            Running.getLogger().info("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@" + channel + " is offline.@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
        }
    }

    public String convertTime(int seconds) {
        StringBuilder sb = new StringBuilder();
        if (seconds >= 60) {
            int minutes = seconds / 60;
            if (minutes >= 60) {
                int hours = minutes / 60;
                if (hours >= 24) {
                    int days = hours / 24;
                    sb.append(days);
                    sb.append("d");
                    sb.append(hours % 24);
                } else {
                    sb.append(hours);
                }
                sb.append("h");
                sb.append(minutes % 60);
            } else {
                sb.append(minutes);
            }
            sb.append("m");
            sb.append(seconds % 60);
        } else {
            sb.append(seconds);
        }

        sb.append("s");

        return sb.toString();
    }

    public BlockingQueue<Command> getStatisticsQueue() {
        return statisticsQueue;
    }

    public BlockingQueue<Command> getMessageQueue() {
        return messageLogger.getLogQueue();
    }


}
