package chatbot.singleton;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import chatbot.enums.ConfigKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static chatbot.enums.ConfigKey.*;

public class Config {

    private final static Logger log = LogManager.getLogger(Config.class);
    private static Map<ConfigKey, String> configMap = new ConcurrentHashMap<>();
    private static Properties properties;

    public static void init() {
        if (configMap == null) {
            configMap = new ConcurrentHashMap<>();
        }
        readProperties();
        //for each config attempts to get the value from an environment variable
        //or if it doesn't exist, attempts to read it from config.properties
        for (ConfigKey configName : values()) {
            String configValue = System.getenv(configName.toString());
            if (configValue == null && properties != null) {
                configValue = properties.getProperty(configName.toString());
            }
            if (configValue != null) {
                configMap.put(configName, configValue);
            }

            log.debug("{}:{}", configName, configName.sensitive ? "***" : configValue);
        }
    }

    private static void readProperties() {
        try (InputStream input = Config.class.getClassLoader()
                .getResourceAsStream("config.properties"))
        {
            properties = new Properties();
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException e) {
            log.error("Error reading properties");
            properties = null;
        }
    }

    public static String getChannelToJoin() {
        String channel = configMap.get(NAM_TWITCH_CHANNEL_TO_JOIN);
        if (channel == null) {
            log.fatal("No channel specified in config.");
            throw new RuntimeException("No channel specified. Cannot continue");
        }
        return channel.toLowerCase();
    }

    public static String getTwitchOauth() {
        return configMap.get(NAM_TWITCH_OAUTH);
    }

    public static String getTwitchUsername() {
        return configMap.get(NAM_TWITCH_USERNAME);
    }

    public static String getTwitchClientId() {
        return configMap.get(NAM_TWITCH_CLIENTID);
    }

    public static String getTwitchSecret() {
        return configMap.get(NAM_TWITCH_SECRET);
    }

    public static String getTwitchUID() {
        return configMap.get(NAM_TWITCH_UID);
    }

    public static String getBotAdmin() {
        return configMap.get(NAM_BOT_ADMIN).toLowerCase();
    }

    public static String getBotWebsite() {
        return configMap.get(NAM_BOT_WEBSITE);
    }

    public static String getMariaUrl() {
        return String.format("jdbc:mariadb://%s:%s/%s",
                configMap.get(NAM_DB_IP),
                configMap.get(NAM_DB_PORT),
                configMap.get(NAM_DB_NAME));
    }

    public static String getPostgresUrl() {
        return String.format("jdbc:postgresql://%s:%s/%s",
                configMap.get(NAM_POSTGRES_IP),
                configMap.get(NAM_POSTGRES_PORT),
                configMap.get(NAM_POSTGRES_NAME));
    }

    public static String getMariaUsername() {
        return configMap.get(NAM_DB_USER);
    }

    public static String getMariaPassword() {
        return configMap.get(NAM_DB_PASSWORD);
    }

    public static String getPostgresUsername() {
        return configMap.get(NAM_POSTGRES_USER);
    }

    public static String getPostgresPassword() {
        return configMap.get(NAM_POSTGRES_PASSWORD);
    }

    public static String getSolrCredentials() {
        return String.format("http://%s:%s/solr/%s",
                configMap.get(NAM_SOLR_IP),
                configMap.get(NAM_SOLR_PORT),
                configMap.get(NAM_SOLR_CORE));
    }
}
