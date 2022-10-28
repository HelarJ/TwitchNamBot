package chatbot.dao;

import chatbot.singleton.SharedStateSingleton;
import chatbot.utils.Config;
import chatbot.utils.HtmlBuilder;
import chatbot.utils.Utils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class ApiHandler {

  private final HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2)
      .build();
  private final String clientID = Config.getTwitchClientId();
  private final String secret = Config.getTwitchSecret();
  private final String channel;
  private final SharedStateSingleton state = SharedStateSingleton.getInstance();
  public String oauth = null;
  private String channelUid;

  public ApiHandler() {
    String channel = Config.getChannelToJoin();
    if (channel.startsWith("#")) {
      this.channel = channel.substring(1);
    } else {
      this.channel = channel;
    }
  }

  /**
   * Attempts to get an oauth from the twitch API, using credentials given in config.
   */
  @SneakyThrows(JsonProcessingException.class)
  public void setOauth() {
    var values = new HashMap<String, String>();
    values.put("client_id", clientID);
    values.put("client_secret", secret);
    values.put("grant_type", "client_credentials");

    String requestBody = new ObjectMapper().writeValueAsString(values);
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(
            "https://id.twitch.tv/oauth2/token?client_id=" + clientID + "&client_secret=" + secret
                + "&grant_type=client_credentials"))
        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
        .build();
    try {
      HttpResponse<String> response = httpClient.send(request,
          HttpResponse.BodyHandlers.ofString());
      JsonNode jsonNode = new ObjectMapper().readTree(response.body());
      oauth = jsonNode.get("access_token").asText();
      int expires = jsonNode.get("expires_in").asInt();
      //the oauth normally expires in ~60 days.
      log.info("Oauth received. Expires in: {}", Utils.convertTime(expires));
    } catch (IOException | InterruptedException | NullPointerException e) {
      log.error("Exception in getting oauth: {}", e.getMessage());
      state.setOnline();
    }
  }

  public String getUID(String username) {
    HttpRequest request = HttpRequest.newBuilder()
        .GET()
        .uri(URI.create("https://api.twitch.tv/helix/users?login=" + username))
        .setHeader("Authorization", "Bearer " + oauth)
        .setHeader("Client-ID", clientID)
        .build();
    try {
      HttpResponse<String> response = httpClient.send(request,
          HttpResponse.BodyHandlers.ofString());
      ObjectMapper mapper = new ObjectMapper();
      String result = response.body();

      JsonNode jsonNode = mapper.readTree(result);
      String userID = jsonNode.findValue("id").asText();
      log.debug("UserID: " + userID);
      return userID;

    } catch (IOException | InterruptedException | NullPointerException e) {
      log.error("Exception in getting UID from name: " + e.getMessage());
      return null;
    }
  }

  public String getFollowList(String username) {
    String userID = getUID(username);
    if (userID == null) {
      return null;
    }
    if (oauth == null) {
      log.fatal("No oauth for followlist.");
      return null;
    }

    HttpRequest request = HttpRequest.newBuilder()
        .GET()
        .uri(URI.create("https://api.twitch.tv/helix/users/follows?from_id=" + userID))
        .setHeader("Authorization", "Bearer " + oauth)
        .setHeader("Client-ID", clientID)
        .build();

    HttpResponse<String> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException | InterruptedException e) {
      log.error("Error getting followed list for " + username + ": " + e.getMessage());
      return null;
    }

    String result = response.body();
    try {
      JsonNode jsonNode = new ObjectMapper().readTree(result);
      int count = Integer.parseInt(jsonNode.get("total").toString());
      var lines = new ArrayList<String>();
      String pagination = null;
      int i = 0;
      while (count > 1) {
        String url = "https://api.twitch.tv/helix/users/follows?first=100&from_id=" + userID;
        if (i > 0) {
          url = "https://api.twitch.tv/helix/users/follows?after=" + pagination
              + "&first=100&from_id=" + userID;
        }
        request = HttpRequest.newBuilder()
            .GET()
            .uri(URI.create(url))
            .setHeader("Authorization", "Bearer " + oauth)
            .setHeader("Client-ID", clientID)
            .build();
        try {
          response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
          log.error("Error getting followed list for {}: {}", username, e.getMessage());
          return null;
        }
        jsonNode = new ObjectMapper().readTree(response.body());
        JsonNode data = jsonNode.get("data");
        JsonNode paginationNode = jsonNode.get("pagination").get("cursor");
        if (paginationNode == null) {
          if (data == null) {
            break;
          }
        } else {
          pagination = paginationNode.asText();
        }
        for (int j = 0; j < data.size(); j++) {
          JsonNode row = data.get(j);
          if (row == null) {
            break;
          }
          lines.add("[%s]\t%s<br>\n"
              .formatted(
                  row.get("followed_at").asText().replaceAll("T", " ").replaceAll("Z", ""),
                  row.get("to_name").asText()));
          i++;
          count--;
        }
      }

      return new HtmlBuilder()
          .withTitle("Followlist for %s ".formatted(username))
          .withBodyIntro("""
              All channels followed by %s.<br>
              Total %d channels followed.<br>
              """.formatted(username, i))
          .withBodyContent(lines)
          .build();
    } catch (JsonProcessingException e) {
      log.error("Error parsing json: {}", e.getMessage());
      return null;
    }
  }

  public void checkOnline() {
    if (channelUid == null) {
      channelUid = getUID(channel);
    }
    if (channelUid == null) {
      return;
    }

    HttpRequest request = HttpRequest.newBuilder()
        .GET()
        .uri(URI.create("https://api.twitch.tv/helix/streams?user_id=" + channelUid))
        .setHeader("Authorization", "Bearer " + oauth)
        .setHeader("Client-ID", clientID)
        .build();

    try {
      HttpResponse<String> response = httpClient.send(request,
          HttpResponse.BodyHandlers.ofString());
      String result = response.body();
      if (result != null) {
        if (result.length() == 27) {
          state.setOffline();
        } else {
          state.setOnline();
        }
      } else {
        state.setOnline();
      }
    } catch (IOException | InterruptedException | NullPointerException e) {
      log.warn("Error getting online status: " + e.getMessage());
    }
  }

  public boolean isLogsApiOnline() {
    HttpRequest request = HttpRequest.newBuilder()
        .GET()
        .uri(URI.create("http://localhost:8069/api/status/"))
        .build();

    try {
      HttpResponse<String> response = httpClient.send(request,
          HttpResponse.BodyHandlers.ofString());
      log.info(response);
      return response.statusCode() == 200;
    } catch (IOException | InterruptedException e) {
      log.warn("Log API is down");
      return false;
    }
  }
}
