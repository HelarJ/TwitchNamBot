package chatbot.dao.db

import chatbot.message.LoggableMessage
import chatbot.message.TimeoutMessage
import chatbot.singleton.SharedState
import chatbot.utils.log
import chatbot.utils.warnSql

import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit


private val queue: LinkedBlockingDeque<LoggableMessage> = LinkedBlockingDeque()

class Postgres : Database, Runnable {
    private var lastFailed: Boolean = false
    private var running: Boolean = true

    private fun getConn(): Connection = postgresInstance.ds.connection

    override fun recordMessage(message: LoggableMessage) {
        queue.addLast(message)
    }

    override fun run() {
        log.info("Started ${this.javaClass}")

        while (SharedState.getInstance().isBotStillRunning && !Thread.currentThread().isInterrupted && running) {
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
                    "INSERT INTO chat_logs.logs.messages(time, username, userid, message, online_status, subscribed, uuid) VALUES (?,?,?,?,?,?,?)"
                ).use { stmt ->
                    stmt.setTimestamp(1, message.timestamp)
                    stmt.setString(2, message.sender)
                    stmt.setInt(3, message.uid.toInt())
                    stmt.setString(4, message.stringMessage)
                    stmt.setBoolean(5, SharedState.getInstance().online.get())
                    stmt.setBoolean(6, message.isSubscribed)
                    stmt.setString(7, message.uuid)
                    stmt.executeUpdate()
                }

                conn.prepareStatement("INSERT INTO chat_logs.logs.full_messages(uuid, full_message) values (?,?)")
                    .use { stmt ->
                        stmt.setString(1, message.uuid)
                        stmt.setString(2, message.fullMsg)
                        stmt.executeUpdate()
                    }

                return true
            }
        } catch (e: SQLException) {
            warnSql(e)
        }
        return false
    }

    override fun recordWhisper(message: LoggableMessage) {
        // TODO
    }

    override fun addTimeout(timeout: TimeoutMessage) {
        // TODO
    }

    override fun addNamListTimeout(timeout: TimeoutMessage) {
        // TODO
    }

    override fun shutdown() {
        running = false
    }
}