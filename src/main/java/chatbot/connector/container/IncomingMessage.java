package chatbot.connector.container;

import java.util.HashMap;
import java.util.Map;

public class IncomingMessage {

  private int index = 0;

  public final String original;
  public String source = "";
  private String command;
  public String params = "";
  public Map<String, String> tagsMap = new HashMap<>();

  public IncomingMessage(String original) {
    this.original = original;
    parseToParts();
  }

  private void parseToParts() {
    if (original.startsWith("@")) {
      int tagsEndIndex = original.indexOf(' ');
      String tags = original.substring(1, tagsEndIndex);
      tagsMap = tagsToMap(tags);
      index = tagsEndIndex + 1;
    }

    if (original.charAt(index) == ':') {
      index += 1;
      int sourceEndIndex = original.indexOf(" ", index);
      source = original.substring(index, sourceEndIndex);
      index = sourceEndIndex + 1;
    }

    int paramsEndIndex = original.indexOf(':', index);
    if (paramsEndIndex == -1) {
      paramsEndIndex = original.length();
    }

    command = original.substring(index, paramsEndIndex).trim();

    if (paramsEndIndex != original.length()) {
      index = paramsEndIndex + 1;
      params = original.substring(index);
    }
  }

  public String getCommand() {
    String[] commandParts = command.split(" ");
    if (commandParts.length == 0) {
      return "";
    }
    return commandParts[0];
  }

  public String getName() {
    return source.substring(0, source.indexOf("!"));
  }


  private Map<String, String> tagsToMap(String tags) {
    Map<String, String> tagsMap = new HashMap<>();
    String[] tagsSplit = tags.split(";");
    for (String tag : tagsSplit) {
      int index = tag.indexOf("=");
      String key = tag.substring(0, index);
      String value = tag.substring(index + 1);
      tagsMap.put(key, value);
    }
    return tagsMap;
  }

  @Override
  public String toString() {
    return """
        original: %s
        tags: %s
        source: %s
        command: %s
        params: %s
        """.formatted(original, tagsMap.toString(), source, command, params);
  }
}
