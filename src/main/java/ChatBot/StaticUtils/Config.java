package ChatBot.StaticUtils;

import ChatBot.enums.ConfigName;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class Config {

    private static Properties properties;
    private static final Map<ConfigName, String> configMap = new HashMap<>();

    public static void initializeCredentials() {
        readProperties();
        //for each config attempts to get the value from an environment variable
        //or if it doesn't exist, attempts to read it from config.properties
        for (ConfigName configName : ConfigName.values()) {
            String configValue = System.getenv(configName.toString());
            if (configValue == null && properties != null) {
                configValue = properties.getProperty(configName.toString());
            }
            configMap.put(configName, configValue);
        }
    }

    public static void readProperties() {
        try (InputStream input = Running.class.getClassLoader().getResourceAsStream("config.properties")) {
            properties = new Properties();
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException e) {
            Running.getLogger().severe("Error reading properties");
            properties = null;
        }
    }

    public static String getChannelToJoin() {
        return configMap.get(ConfigName.NAM_TWITCH_CHANNEL_TO_JOIN);
    }

    public static String getTwitchOauth() {
        return configMap.get(ConfigName.NAM_TWITCH_OAUTH);
    }

    public static String getTwitchUsername() {
        return configMap.get(ConfigName.NAM_TWITCH_USERNAME);
    }

    public static String getTwitchClientId() {
        return configMap.get(ConfigName.NAM_TWITCH_CLIENTID);
    }

    public static String getTwitchSecret() {
        return configMap.get(ConfigName.NAM_TWITCH_SECRET);
    }

    public static String getTwitchUID() {
        return configMap.get(ConfigName.NAM_TWITCH_UID);
    }

    public static String getFtpServer() {
        return configMap.get(ConfigName.NAM_FTP_SERVER);
    }

    public static String getFtpPort() {
        return configMap.get(ConfigName.NAM_FTP_PORT);
    }

    public static String getFtpUsername() {
        return configMap.get(ConfigName.NAM_FTP_USERNAME);
    }

    public static String getFtpPassword() {
        return configMap.get(ConfigName.NAM_FTP_PASSWORD);
    }

    public static String getBotAdmin() {
        return configMap.get(ConfigName.NAM_BOT_ADMIN).toLowerCase();
    }

    public static String getBotWebsite() {
        return configMap.get(ConfigName.NAM_BOT_WEBSITE);
    }

    public static String getSQLCredentials() {
        return String.format("jdbc:mariadb://%s:%s/%s?user=%s&password=%s",
                configMap.get(ConfigName.NAM_DB_IP),
                configMap.get(ConfigName.NAM_DB_PORT),
                configMap.get(ConfigName.NAM_DB_NAME),
                configMap.get(ConfigName.NAM_DB_USER),
                configMap.get(ConfigName.NAM_DB_PASSWORD)
        );
    }

    public static String getSolrCredentials() {
        return String.format("http://%s:%s/solr/%s",
                configMap.get(ConfigName.NAM_SOLR_IP),
                configMap.get(ConfigName.NAM_SOLR_PORT),
                configMap.get(ConfigName.NAM_SOLR_CORE)
        );
    }

}
