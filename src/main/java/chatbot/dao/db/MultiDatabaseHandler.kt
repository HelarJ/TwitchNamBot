package chatbot.dao.db

import chatbot.message.LoggableMessage
import chatbot.message.TimeoutMessage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MultiDatabaseHandler(private vararg val loggers: Database) : Database {
    private val executor: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

    init {
        loggers.forEach {
            if (it is Runnable) {
                executor.submit(it)
            }
        }
    }

    override fun recordMessage(message: LoggableMessage) = loggers.forEach { it.recordMessage(message) }

    override fun recordWhisper(message: LoggableMessage) = loggers.forEach { it.recordWhisper(message) }

    override fun addTimeout(timeout: TimeoutMessage) = loggers.forEach { it.addTimeout(timeout) }

    override fun addNamListTimeout(timeout: TimeoutMessage) = loggers.forEach { it.addNamListTimeout(timeout) }

    fun destroy() {
        executor.shutdownNow()
    }
}