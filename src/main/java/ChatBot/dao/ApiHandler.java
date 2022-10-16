package ChatBot.dao;

import ChatBot.Service.OnlineCheckerService;
import ChatBot.StaticUtils.Config;
import ChatBot.StaticUtils.Running;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.logging.Logger;

public class ApiHandler {
    private final Logger logger = Logger.getLogger(ApiHandler.class.toString());
    private final HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
    private final String clientID = Config.getTwitchClientId();
    private final String secret = Config.getTwitchSecret();
    private final String channel;
    private final String oauth;

    public ApiHandler() {
        String channel = Config.getChannelToJoin();
        if (channel.startsWith("#")) {
            this.channel = channel.substring(1);
        } else {
            this.channel = channel;
        }
        this.oauth = getOauth();
    }

    public String getOauth() {
        var values = new HashMap<String, String>();
        values.put("client_id", clientID);
        values.put("client_secret", secret);
        values.put("grant_type", "client_credentials");

        var objectMapper = new ObjectMapper();

        String requestBody = "";
        try {
            requestBody = objectMapper
                    .writeValueAsString(values);
        } catch (JsonProcessingException e) {
            logger.severe("Unable to process json.");
        }
        String oauthToken = null;
        int expires;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://id.twitch.tv/oauth2/token?client_id=" + clientID + "&client_secret=" + secret + "&grant_type=client_credentials"))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        while (oauthToken == null && Running.isBotStillRunning()) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                ObjectMapper mapper = new ObjectMapper();
                String result = response.body();
                JsonNode jsonNode = mapper.readTree(result);
                oauthToken = jsonNode.get("access_token").asText();
                expires = jsonNode.get("expires_in").asInt();
                logger.info(expires + " " + oauthToken);
            } catch (IOException | InterruptedException | NullPointerException e) {
                logger.severe("Exception in getting oauth. Defaulting to online mode and retrying: " + e.getMessage());
                Running.setOnline();
                oauthToken = null;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
            }
        }
        return oauthToken;
    }

    public String getUID(String username) {
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("https://api.twitch.tv/helix/users?login=" + username))
                .setHeader("Authorization", "Bearer " + oauth)
                .setHeader("Client-ID", clientID)
                .build();
        String userID = null;
        while (userID == null && Running.isBotStillRunning()) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                ObjectMapper mapper = new ObjectMapper();
                String result = response.body();

                JsonNode jsonNode = mapper.readTree(result);
                result = jsonNode.get("data").toString();
                result = result.substring(1, result.length() - 1);
                logger.info(result);
                jsonNode = mapper.readTree(result);
                userID = jsonNode.get("id").asText();

            } catch (IOException | InterruptedException | NullPointerException e) {
                logger.severe("Exception in getting UID from name: " + e.getMessage());
                return null;
            }
        }
        return userID;
    }

    public String getFollowList(String username) {
        String userID = getUID(username);
        if (userID == null) {
            return null;
        }
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("https://api.twitch.tv/helix/users/follows?from_id=" + userID))
                .setHeader("Authorization", "Bearer " + oauth)
                .setHeader("Client-ID", clientID)
                .build();

        HttpResponse<String> response = null;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            logger.warning("Error getting followed list for " + username + ": " + e.getMessage());
        }
        String result;
        if (response != null) {
            result = response.body();
            ObjectMapper mapper = new ObjectMapper();
            int count;
            try {
                JsonNode jsonNode = mapper.readTree(result);
                result = jsonNode.get("total").toString();
                count = Integer.parseInt(result);
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
                sb.append("Followlist for ");
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
                sb.append("All channels followed by ");
                sb.append(username);
                sb.append(".");
                sb.append("<br>\n");
                sb.append("Total ");
                sb.append(count);
                sb.append(" channels followed.");
                sb.append("<br>\n");
                sb.append("=========================================================");
                sb.append("<br>\n");
                sb.append("<br>\n");
                String pagination = null;
                int i = 0;
                while (count > 1) {
                    String url = "https://api.twitch.tv/helix/users/follows?first=100&from_id=" + userID;
                    if (i > 0) {
                        url = "https://api.twitch.tv/helix/users/follows?after=" + pagination + "&first=100&from_id=" + userID;
                    }
                    request = HttpRequest.newBuilder()
                            .GET()
                            .uri(URI.create(url))
                            .setHeader("Authorization", "Bearer " + oauth)
                            .setHeader("Client-ID", clientID)
                            .build();
                    response = null;
                    try {
                        response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    } catch (IOException | InterruptedException e) {
                        logger.warning("Error getting followed list for " + username + ": " + e.getMessage());
                    }
                    if (response != null) {
                        result = response.body();
                        mapper = new ObjectMapper();
                        jsonNode = mapper.readTree(result);
                        JsonNode data = jsonNode.get("data");
                        JsonNode paginationNode = jsonNode.get("pagination").get("cursor");
                        if (paginationNode == null) {
                            if (data == null) {
                                break;
                            }
                        } else {
                            pagination = paginationNode.asText();
                        }
                        int currentsize = data.size();
                        int j = 0;
                        while (j < currentsize) {
                            JsonNode row = data.get(j);
                            if (row == null) {
                                break;
                            }
                            sb.append("[");
                            sb.append(row.get("followed_at").asText().replaceAll("T", " ").replaceAll("Z", ""));
                            sb.append("]");
                            sb.append("\t");
                            sb.append(row.get("to_name").asText());
                            sb.append("<br>\n");
                            j++;
                            i++;
                            count--;
                        }
                    }
                }

                sb.append("<br>\n");
                sb.append("END OF FILE");
                sb.append("<br>\n");
                sb.append("</body>");
                sb.append("\r\n");
                sb.append("</html>");
                sb.append("\r\n");
                return sb.toString();
            } catch (JsonProcessingException e) {
                logger.severe("Error parsing json.");
            }
        } else {
            return null;
        }
        return null;
    }

    public void checkOnline(OnlineCheckerService onlineCheckerService) {
        String userID = getUID(channel);
        if (userID == null) {
            logger.warning("Error getting UID from API. Retrying in 5s.");
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {
            }
            return;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("https://api.twitch.tv/helix/streams?user_id=" + userID))
                .setHeader("Authorization", "Bearer " + oauth)
                .setHeader("Client-ID", clientID)
                .build();

        try {
            while (Running.isBotStillRunning() && onlineCheckerService.running) {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                String result = response.body();
                if (result != null) {
                    if (result.length() == 27) { //0 if the API is not responding.
                        Running.setOffline();
                    } else {
                        Running.setOnline();
                    }
                } else {
                    Running.setOnline();
                }
                Thread.sleep(3000);
            }

        } catch (IOException | InterruptedException | NullPointerException e) {
            logger.severe("Error getting online status: " + e.getMessage());
        }
    }
}
