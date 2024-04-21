//package chatbot.dao;
//
//
//import chatbot.dao.db.DatabaseHandler;
//import chatbot.enums.Response;
//import chatbot.message.LoggableMessage;
//import chatbot.message.TimeoutMessage;
//import chatbot.utils.Utils;
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.Comparator;
//import java.util.HashMap;
//import java.util.LinkedHashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.Map.Entry;
//import java.util.Optional;
//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;
//
//public class InMemoryDatabase implements DatabaseHandler {
//  private final static Logger log = LogManager.getLogger(InMemoryDatabase.class);
//
//  private final List<String> modlist = new ArrayList<>();
//  private final Map<String, List<String>> alts = new HashMap<>();
//  private final List<TimeoutMessage> timeouts = new ArrayList<>();
//  private final List<TimeoutMessage> namListTimeouts = new ArrayList<>();
//  private final List<LoggableMessage> whispers = new ArrayList<>();
//  private final List<LoggableMessage> messages = new ArrayList<>();
//  private final List<String> disabledList = new ArrayList<>();
//  private final Map<String, String> blackList = new HashMap<>();
//  private final HashMap<String, Boolean> userPermissions = new HashMap<>();
//
//  @Override
//  public int getMessageCount(String username) {
//    return messages
//        .stream()
//        .filter(loggableMessage -> loggableMessage.getSender().equalsIgnoreCase(username))
//        .toList()
//        .size();
//  }
//
//  @Override
//  public List<String> getModList() {
//    return modlist;
//  }
//
//  @Override
//  public List<String> getAltsList() {
//    return new ArrayList<>();
//  }
//
//
//  @Override
//  public void addTimeout(TimeoutMessage timeout) {
//    log.info(timeout);
//    timeouts.add(timeout);
//  }
//
//  @Override
//  public int getTimeoutAmount(String username) {
//    return namListTimeouts
//        .stream()
//        .filter(timeout -> timeout.getUsername().equalsIgnoreCase(username))
//        .mapToInt(TimeoutMessage::getLength)
//        .sum();
//  }
//
//  @Override
//  public Optional<String> getTopTimeouts() {
//    final Map<String, Integer> totalTimeoutMap = new LinkedHashMap<>();
//    namListTimeouts.forEach(timeout -> {
//      totalTimeoutMap.putIfAbsent(timeout.getUsername(), 0);
//      totalTimeoutMap.put(timeout.getUsername(), timeout.getLength());
//    });
//
//    List<Entry<String, Integer>> entries = totalTimeoutMap.entrySet()
//        .stream()
//        .sorted(Comparator.comparingInt(Entry::getValue))
//        .limit(10L)
//        .toList();
//
//    final List<String> nammerList = new ArrayList<>();
//    for (Entry<String, Integer> entry : entries) {
//      nammerList.add("%s: %s".formatted(
//          entry.getKey(),
//          Utils.convertTime(entry.getValue())));
//
//    }
//    return Optional.of(Utils.formatNammerList(nammerList));
//  }
//
//  @Override
//  public void addNamListTimeout(TimeoutMessage timeout) {
//    log.info(timeout);
//    namListTimeouts.add(timeout);
//  }
//
//  @Override
//  public void recordWhisper(LoggableMessage message) {
//    log.info(message);
//    whispers.add(message);
//  }
//
//  @Override
//  public void recordMessage(LoggableMessage message) {
//    log.info(message);
//    messages.add(message);
//  }
//
//  @Override
//  public Optional<String> firstOccurrence(String msg) {
//    return messages
//        .stream()
//        .map(LoggableMessage::getStringMessage)
//        .filter(stringMessage -> stringMessage.contains(msg))
//        .findFirst();
//  }
//
//  @Override
//  public String firstMessage(String username) {
//    return messages
//        .stream()
//        .filter(loggableMessage -> loggableMessage.getSender().equalsIgnoreCase(username))
//        .map(LoggableMessage::getStringMessage)
//        .findFirst()
//        .orElse(Response.INTERNAL_ERROR.toString());
//  }
//
//  @Override
//  public String lastMessage(String username) {
//    return messages
//        .stream()
//        .sorted(Collections.reverseOrder())
//        .filter(loggableMessage -> loggableMessage.getSender().equalsIgnoreCase(username))
//        .map(LoggableMessage::getStringMessage)
//        .findFirst()
//        .orElse(Response.INTERNAL_ERROR.toString());
//  }
//
//  @Override
//  public Optional<String> randomSearch(String username, String msg) {
//    List<String> filteredMsgs = new ArrayList<>(messages
//        .stream()
//        .filter(loggableMessage -> loggableMessage.getSender().equalsIgnoreCase(username))
//        .map(LoggableMessage::getStringMessage)
//        .filter(stringMessage -> stringMessage.contains(msg))
//        .toList());
//    Collections.shuffle(filteredMsgs);
//    if (filteredMsgs.size() == 0) {
//      return Optional.empty();
//    } else {
//      return Optional.of(filteredMsgs.get(0));
//    }
//  }
//
//  @Override
//  public Optional<String> randomQuote(String username, String year) {
//    List<String> filteredMsgs = new ArrayList<>(messages
//        .stream()
//        .filter(loggableMessage -> loggableMessage.getSender().equalsIgnoreCase(username))
//        .map(LoggableMessage::getStringMessage)
//        .toList());
//    Collections.shuffle(filteredMsgs);
//    if (filteredMsgs.size() == 0) {
//      return Optional.empty();
//    } else {
//      return Optional.of(filteredMsgs.get(0));
//    }
//  }
//
//  @Override
//  public long search(String msg) {
//    return messages
//        .stream()
//        .map(LoggableMessage::getStringMessage)
//        .filter(stringMessage -> stringMessage.contains(msg))
//        .toList()
//        .size();
//  }
//
//  @Override
//  public long searchUser(String username, String msg) {
//    return messages
//        .stream()
//        .filter(loggableMessage -> loggableMessage.getSender().equalsIgnoreCase(username))
//        .map(LoggableMessage::getStringMessage)
//        .filter(stringMessage -> stringMessage.contains(msg))
//        .toList()
//        .size();
//  }
//
//  @Override
//  public Map<String, String> getBlacklist() {
//    return blackList;
//  }
//
//  @Override
//  public List<String> getDisabledList() {
//    return disabledList;
//  }
//
//  @Override
//  public void addDisabled(String from, String username) {
//    log.info("addDisabled {} {}", from, username);
//    disabledList.add("%s %s".formatted(from, username));
//  }
//
//
//  @Override
//  public void removeDisabled(String username) {
//    log.info("removeDisabled {}", username);
//    disabledList.remove(username);
//  }
//
//  @Override
//  public Optional<List<String>> getAlternateNames(String username) {
//    return Optional.ofNullable(alts.get(username));
//  }
//
//  @Override
//  public boolean addAlt(String main, String alt) {
//    log.info("addAlt {} {}", main, alt);
//    alts.putIfAbsent(main, new ArrayList<>());
//    alts.get(main).add(alt);
//    return true;
//  }
//
//  @Override
//  public void setCommandPermissionUser(String user, String command, boolean enable) {
//    log.info("setCommandPermissionUser {} {} {}", user, command, enable);
//  }
//
//  @Override
//  public HashMap<String, Boolean> getPersonalPermissions(String user) {
//    return userPermissions;
//  }
//}
