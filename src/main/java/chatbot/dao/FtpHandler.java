package chatbot.dao;

import chatbot.singleton.ConfigSingleton;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

@Log4j2
public class FtpHandler {

    private final ConfigSingleton config = ConfigSingleton.getInstance();
    private final FTPClient ftpClient = new FTPClient();

    public FtpHandler() {
        connect();
    }

    public void connect() {
        try {
            ftpClient.connect(config.getFtpServer(), Integer.parseInt(config.getFtpPort()));
            int reply = ftpClient.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                ftpClient.disconnect();
                throw new IOException("Exception in connecting to FTP Server");
            }
            ftpClient.login(config.getFtpUsername(), config.getFtpPassword());
        } catch (IOException e) {
            log.error("Error connecting to FTP: " + e.getMessage());
        }
    }

    @SneakyThrows(IOException.class)
    public void disconnect() {
        if (ftpClient.isConnected()) {
            ftpClient.disconnect();
        }
    }

    public boolean upload(String filename, String text) {
        boolean success = false;
        try {
            ftpClient.enterLocalPassiveMode();
            success = ftpClient.storeFile(filename,
                new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
            log.info(ftpClient.getReplyString());
            ftpClient.enterLocalActiveMode();
        } catch (IOException e) {
            log.error("Error writing to outputstream: " + e.getMessage());
        } catch (NullPointerException e) {
            log.error("Error uploading to ftp");
        } finally {
            disconnect();
        }
        return success;
    }

    public void cleanLogs() {
        if (!ftpClient.isConnected()) {
            connect();
        }
        ftpClient.enterLocalPassiveMode();
        FTPFile[] files = new FTPFile[0];
        try {
            files = ftpClient.listFiles(".");
        } catch (IOException e) {
            log.error("IO Error while getting list of files from ftp.");
        }

        try {
            for (FTPFile file : files) {
                String filename = file.getName();
                if (filename.equals(".ftpquota") || filename.equals(".") || filename.equals("..")) {
                    continue;
                }
                if (file.getTimestamp().toInstant().plus(240, ChronoUnit.HOURS)
                    .isBefore(Instant.now())) {
                    log.info("Deleted " + filename);
                    ftpClient.deleteFile(filename);
                }
            }
            ftpClient.enterLocalActiveMode();
        } catch (IOException e) {
            log.error("Error deleting file");
        } finally {
            disconnect();
        }
    }
}

