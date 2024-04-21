package chatbot.dao.api;

import chatbot.singleton.Config;
import chatbot.singleton.SharedState;
import chatbot.utils.Utils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ApiHandler {
    private final static Logger log = LogManager.getLogger(ApiHandler.class);

    private final Config config = Config.getInstance();
    private final HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2)
            .build();
    private final String clientID = config.getTwitchClientId();
    private final String secret = config.getTwitchSecret();
    private final String channel;
    private final SharedState state = SharedState.getInstance();
    public String oauth = null;
    private String channelUid;

    public ApiHandler() {
        String channel = config.getChannelToJoin();
        if (channel.startsWith("#")) {
            this.channel = channel.substring(1);
        } else {
            this.channel = channel;
        }
    }

    /**
     * Attempts to get an oauth from the twitch API, using credentials given in config.
     */
    public void setOauth() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "https://id.twitch.tv/oauth2/token?client_id=" + clientID + "&client_secret=" + secret
                                + "&grant_type=client_credentials")
                )
                .POST(HttpRequest.BodyPublishers.noBody())
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
            state.setOnline("Exception getting oauth.");
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
            log.debug("UserID: {}", userID);
            return userID;

        } catch (IOException | InterruptedException | NullPointerException e) {
            log.error("Exception in getting UID from name: {}", e.getMessage());
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
                    state.setOffline(result);
                } else {
                    state.setOnline(result);
                }
            } else {
                state.setOnline(result);
            }
        } catch (IOException | InterruptedException | NullPointerException e) {
            log.warn("Error getting online status: {}", e.getMessage());
        }
    }
}
