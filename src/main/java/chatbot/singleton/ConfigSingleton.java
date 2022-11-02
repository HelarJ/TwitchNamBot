package chatbot.singleton;

import chatbot.ConsoleMain;
import chatbot.enums.ConfigName;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class ConfigSingleton {

  private final Map<ConfigName, String> configMap = new HashMap<>();
  private Properties properties;

  private static ConfigSingleton configSingleton;

  private ConfigSingleton() {
    initializeConfig();
  }

  public static ConfigSingleton getInstance() {
    if (configSingleton == null) {
      configSingleton = new ConfigSingleton();
    }
    return configSingleton;
  }

  public void initializeConfig() {
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

  private void readProperties() {
    try (InputStream input = ConsoleMain.class.getClassLoader()
        .getResourceAsStream("config.properties")) {
      properties = new Properties();
      if (input != null) {
        properties.load(input);
      }
    } catch (IOException e) {
      log.error("Error reading properties");
      properties = null;
    }
  }

  public String getChannelToJoin() {
    String channel = configMap.get(ConfigName.NAM_TWITCH_CHANNEL_TO_JOIN);
    if (channel == null) {
      log.fatal("No channelname specified in config.");
      throw new RuntimeException("No channelname specified. Cannot continue");
    }
    return channel.toLowerCase();
  }

  public String getTwitchOauth() {
    return configMap.get(ConfigName.NAM_TWITCH_OAUTH);
  }

  public String getTwitchUsername() {
    return configMap.get(ConfigName.NAM_TWITCH_USERNAME);
  }

  public String getTwitchClientId() {
    return configMap.get(ConfigName.NAM_TWITCH_CLIENTID);
  }

  public String getTwitchSecret() {
    return configMap.get(ConfigName.NAM_TWITCH_SECRET);
  }

  public String getTwitchUID() {
    return configMap.get(ConfigName.NAM_TWITCH_UID);
  }

  public String getBotAdmin() {
    return configMap.get(ConfigName.NAM_BOT_ADMIN).toLowerCase();
  }

  public String getBotWebsite() {
    return configMap.get(ConfigName.NAM_BOT_WEBSITE);
  }

  public String getSqlUrl() {
    return String.format("jdbc:mariadb://%s:%s/%s",
        configMap.get(ConfigName.NAM_DB_IP),
        configMap.get(ConfigName.NAM_DB_PORT),
        configMap.get(ConfigName.NAM_DB_NAME));
  }

  public String getSqlUsername() {
    return configMap.get(ConfigName.NAM_DB_USER);
  }

  public String getSqlPassword() {
    return configMap.get(ConfigName.NAM_DB_PASSWORD);
  }

  public String getSolrCredentials() {
    return String.format("http://%s:%s/solr/%s",
        configMap.get(ConfigName.NAM_SOLR_IP),
        configMap.get(ConfigName.NAM_SOLR_PORT),
        configMap.get(ConfigName.NAM_SOLR_CORE));
  }
}
