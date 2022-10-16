package ChatBot.dao;

import ChatBot.Dataclass.Timeout;
import ChatBot.StaticUtils.Config;
import ChatBot.StaticUtils.Running;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;

public class SQLHandler {

    private static final Logger logger = Logger.getLogger(SQLHandler.class.toString());
    private final String credentials;

    public SQLHandler() {
        this.credentials = Config.getSQLCredentials();
    }

    public void addNamListTimeoutToDatabase(Timeout timeout) {
        try (Connection conn = DriverManager.getConnection(credentials);
             PreparedStatement stmt = conn.prepareStatement("select chat_stats.f_add_timeout(?,?);"))
        {
            String username = timeout.getUsername();
            int length = timeout.getLength();

            stmt.setString(1, username);
            stmt.setInt(2, length);
            ResultSet result = stmt.executeQuery();
            result.next();
            if (result.getBoolean(1)) {
                logger.info("Added " + username + " with a timeout of " + length + "s to db.");
            } else {
                logger.warning("Failed to add " + username + " with a timeout of " + length + "s to db.");
            }

        } catch (SQLException e) {
            logger.severe("SQLException: " + e.getMessage() + ", VendorError: " + e.getErrorCode());
        }
    }

    public void addUsernameToDatabase(Timeout timeout) {
        try (Connection conn = DriverManager.getConnection(credentials);
             PreparedStatement stmt = conn.prepareStatement("select chat_stats.f_add_user(?);"))
        {
            stmt.setString(1, timeout.getUsername());
            stmt.executeQuery();
        } catch (SQLException ignored) {
        }
    }

    public void addTimeoutToDatabase(Timeout timeout) {
        try (Connection conn = DriverManager.getConnection(credentials);
             PreparedStatement stmt = conn.prepareStatement("CALL chat_stats.sp_log_timeout(?,?,?,?);"))
        {
            stmt.setString(1, timeout.getUsername());
            stmt.setString(2, timeout.getUserid());
            stmt.setInt(3, timeout.getLength());
            stmt.setBoolean(4, Running.online);
            stmt.executeQuery();
        } catch (SQLException ex) {
            Running.getLogger().severe("SQL ERROR: " + "SQLException: " + ex.getMessage() + ", VendorError: " + ex.getErrorCode());
        }
    }

    public int getTimeoutAmount(String username) {
        try (Connection conn = DriverManager.getConnection(credentials);
             PreparedStatement stmt = conn.prepareStatement("call chat_stats.sp_get_usernam(?)"))
        {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            rs.next();
            return rs.getInt("timeout");

        } catch (SQLException ex) {
            Running.getLogger().warning("SQL ERROR: " + "SQLException: " + ex.getMessage() + ", VendorError: " + ex.getErrorCode());
        }
        return 0;
    }
}
