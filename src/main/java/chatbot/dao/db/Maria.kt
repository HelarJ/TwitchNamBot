package chatbot.dao.db

import chatbot.message.LoggableMessage
import chatbot.message.TimeoutMessage
import chatbot.singleton.SharedState
import chatbot.utils.errorSql
import chatbot.utils.log
import chatbot.utils.warnSql
import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

private val queue: LinkedBlockingDeque<LoggableMessage> = LinkedBlockingDeque()

class Maria : Database, Runnable {
    private var lastFailed: Boolean = false

    private fun getConn(): Connection = mariaInstance.ds.connection

    override fun recordMessage(message: LoggableMessage) {
        queue.addLast(message)
    }

    override fun run() {
        log.info("Started ${this.javaClass}")

        while (SharedState.getInstance().isBotStillRunning && !Thread.currentThread().isInterrupted) {
            val message: LoggableMessage = queue.pollFirst(1, TimeUnit.SECONDS) ?: continue

            if (!logMessage(message)) {
                queue.addFirst(message) // add it back if the insert failed.

                if (queue.size > 10000) {
                    log.error("Queue has reached high capacity ${queue.size}")
                }

                lastFailed = true
            } else {
                lastFailed = false
            }

            if (lastFailed) {
                Thread.sleep(500)
            }
        }

        log.info("Shut down ${this.javaClass}")
    }

    private fun logMessage(message: LoggableMessage): Boolean {
        try {
            getConn().use { conn ->
                conn.prepareStatement(
                    "INSERT INTO chat_stats.messages(time, username, userid, message, online_status, subscribed, full, uuid) VALUES (?,?,?,?,?,?,?,?)"
                ).use { stmt ->
                    stmt.setTimestamp(1, message.timestamp)
                    stmt.setString(2, message.sender)
                    stmt.setString(3, message.uid)
                    stmt.setString(4, message.stringMessage)
                    stmt.setBoolean(5, SharedState.getInstance().online.get())
                    stmt.setBoolean(6, message.isSubscribed)
                    stmt.setString(7, message.fullMsg)
                    stmt.setString(8, message.uuid)
                    stmt.executeQuery()
                    return true
                }
            }
        } catch (e: SQLException) {
            warnSql(e)
        }
        return false
    }

    override fun recordWhisper(message: LoggableMessage) {
        try {
            getConn().use { conn ->
                conn.prepareStatement(
                    "CALL chat_stats.sp_log_whisper(?,?,?);"
                ).use { stmt ->
                    stmt.setTimestamp(1, message.timestamp)
                    stmt.setString(2, message.sender)
                    stmt.setString(3, message.stringMessage)
                    stmt.executeQuery()
                }
            }
        } catch (e: SQLException) {
            errorSql(e)
        }
    }

    override fun addTimeout(timeout: TimeoutMessage) {
        try {
            getConn().use { conn ->
                conn.prepareStatement(
                    "CALL chat_stats.sp_log_timeout(?,?,?,?);"
                ).use { stmt ->
                    stmt.setString(1, timeout.username)
                    stmt.setString(2, timeout.userid)
                    stmt.setInt(3, timeout.length)
                    stmt.setBoolean(4, SharedState.getInstance().online.get())
                    stmt.executeQuery()
                }
            }
        } catch (e: SQLException) {
            errorSql(e)
        }
    }

    override fun addNamListTimeout(timeout: TimeoutMessage) {
        try {
            getConn().use { conn ->
                conn.prepareStatement(
                    "call chat_stats.sp_add_timeout(?,?);"
                ).use { stmt ->
                    val username = timeout.username
                    val length = timeout.length

                    stmt.setString(1, username)
                    stmt.setInt(2, length)
                    stmt.executeQuery()
                    log.info("Added {} with a timeout of {}s to db.", username, length)
                }
            }
        } catch (e: SQLException) {
            errorSql(e)
        }
    }
}