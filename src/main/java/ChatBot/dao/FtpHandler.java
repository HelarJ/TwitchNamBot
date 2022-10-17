package ChatBot.dao;

import ChatBot.StaticUtils.Config;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.logging.Logger;

public class FtpHandler {
    private final static Logger logger = Logger.getLogger(FtpHandler.class.toString());
    private final FTPClient ftpClient;

    public FtpHandler() {
        ftpClient = new FTPClient();
        try {
            ftpClient.connect(Config.getFtpServer(), Integer.parseInt(Config.getFtpPort()));
            int reply = ftpClient.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                ftpClient.disconnect();
                throw new IOException("Exception in connecting to FTP Server");
            }
            ftpClient.login(Config.getFtpUsername(), Config.getFtpPassword());
        } catch (IOException e) {
            logger.warning("Error connecting to FTP: " + e.getMessage());
        }
    }

    public boolean upload(String filename, String text) throws IOException, NullPointerException {

        ftpClient.enterLocalPassiveMode();
        boolean success = ftpClient.storeFile(filename, new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        logger.info(ftpClient.getReplyString());
        ftpClient.enterLocalActiveMode();

        return success;
    }

    public void cleanLogs() {
        ftpClient.enterLocalPassiveMode();
        FTPFile[] files = new FTPFile[0];
        try {
            files = ftpClient.listFiles(".");
        } catch (IOException e) {
            logger.warning("IO Error while getting list of files from ftp.");
        }

        try {
            for (FTPFile file : files) {
                String filename = file.getName();
                if (filename.equals(".ftpquota") || filename.equals(".") || filename.equals("..")) {
                    continue;
                }
                if (file.getTimestamp().toInstant().plus(240, ChronoUnit.HOURS).isBefore(Instant.now())) {
                    logger.info("Deleted" + filename);
                    ftpClient.deleteFile(filename);
                }
            }
        } catch (IOException e) {
            logger.warning("Error deleting file");
        } finally {
            ftpClient.enterLocalActiveMode();
        }

    }
}

