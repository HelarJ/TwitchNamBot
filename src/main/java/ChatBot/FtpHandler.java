package ChatBot;

import ChatBot.StaticUtils.Running;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Properties;

public class FtpHandler {
    private final FTPClient ftpClient;

    public FtpHandler() {
        Properties p = Running.getProperties();
        ftpClient = new FTPClient();
        try {
            ftpClient.connect(p.getProperty("ftp.server"), Integer.parseInt(p.getProperty("ftp.port")));
            int reply = ftpClient.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                ftpClient.disconnect();
                throw new IOException("Exception in connecting to FTP Server");
            }
            ftpClient.login(p.getProperty("ftp.username"), p.getProperty("ftp.password"));
        } catch (IOException e) {
            Running.getLogger().warning("Error connecting to FTP: " + e.getMessage());
        }
    }

    public boolean upload(String filename, String text) throws IOException, NullPointerException {

        ftpClient.enterLocalPassiveMode();
        boolean success = ftpClient.storeFile(filename, new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        Running.getLogger().info(ftpClient.getReplyString());
        ftpClient.enterLocalActiveMode();

        return success;
    }

    public void cleanLogs() {
        ftpClient.enterLocalPassiveMode();
        FTPFile[] files = new FTPFile[0];
        try {
            files = ftpClient.listFiles(".");
        } catch (IOException e) {
            Running.getLogger().warning("IO Error while getting list of files from ftp.");
        }

        try {
            for (FTPFile file : files) {
                String filename = file.getName();
                if (filename.equals(".ftpquota") || filename.equals(".") || filename.equals("..")) {
                    continue;
                }
                if (file.getTimestamp().toInstant().plus(240, ChronoUnit.HOURS).isBefore(Instant.now())) {
                    Running.getLogger().info("Deleted" + filename);
                    ftpClient.deleteFile(filename);
                }
            }
        } catch (IOException e) {
            Running.getLogger().warning("Error deleting file");
        } finally {
            ftpClient.enterLocalActiveMode();
        }

    }
}

