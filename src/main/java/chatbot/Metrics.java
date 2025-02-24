package chatbot;

import chatbot.singleton.Config;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;

import java.time.Instant;

public enum Metrics {
    CONNECT_COUNTER("connect"),
    RECONNECT_COUNTER("reconnect"),
    MESSAGE_COUNTER("message"),
    COMMAND_COUNTER("command"),
    COMMAND_NOT_ALLOWED_COUNTER("command_not_allowed"),
    PING_COUNTER("ping"),
    WHISPER_COUNTER("whisper"),
    SENT_COUNTER("sent"),
    BLACKLIST_COUNTER("blacklist"),
    TIMEOUT_COUNTER("timeout"),
    PERMABAN_COUNTER("permaban"),
    UNHANDLED_COUNTER("unhandled"),
    ;

    final String field;

    Metrics(String field) {
        this.field = field;
    }

    public void inc() {
        inc(null, null);
    }

    public void inc(String command) {
        inc(command, null);
    }

    public void inc(String command, Long time) {
        Point point = Point.measurement("bot").addField(field, time != null ? time : 1L).time(Instant.now().toEpochMilli(), WritePrecision.MS);
        if (command != null) {
            point.addTag("command", command);
        }

        writeApi.writePoint(point);
    }

    static InfluxDBClient influxDBClient;
    static WriteApi writeApi;


    public static void register() {
        influxDBClient = InfluxDBClientFactory.create(Config.getInfluxAddress(), Config.getInfluxToken().toCharArray(), "bot", "bot");
        writeApi = influxDBClient.makeWriteApi();
    }

    public static void close() {
        influxDBClient.close();
    }
}
