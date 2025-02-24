package chatbot.enums;

public enum ConfigKey {
    NAM_DB_USER,
    NAM_DB_PASSWORD(true),
    NAM_DB_IP,
    NAM_DB_PORT,
    NAM_DB_NAME,
    NAM_POSTGRES_USER,
    NAM_POSTGRES_PASSWORD(true),
    NAM_POSTGRES_IP,
    NAM_POSTGRES_PORT,
    NAM_POSTGRES_NAME,
    NAM_SOLR_IP,
    NAM_SOLR_PORT,
    NAM_SOLR_CORE,
    NAM_TWITCH_USERNAME,
    NAM_TWITCH_CLIENTID,
    NAM_TWITCH_SECRET(true),
    NAM_TWITCH_OAUTH(true),
    NAM_TWITCH_UID,
    NAM_TWITCH_CHANNEL_TO_JOIN,
    NAM_BOT_WEBSITE,
    NAM_BOT_ADMIN,
    TEST_MODE,
    NAM_INFLUX_ADDRESS,
    NAM_INFLUX_TOKEN,
    ;

    public final boolean sensitive;

    ConfigKey() {
        sensitive = false;
    }

    ConfigKey(boolean sensitive) {
        this.sensitive = sensitive;
    }
}
