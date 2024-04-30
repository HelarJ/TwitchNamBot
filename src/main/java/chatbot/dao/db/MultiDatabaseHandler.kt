package chatbot.dao.db

import chatbot.message.LoggableMessage
import chatbot.message.TimeoutMessage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MultiDatabaseHandler(private vararg val loggers: Database) : Database {
    private val executor: ExecutorService = Executors.newThreadPerTaskExecutor(Executors.defaultThreadFactory())

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

    override fun shutdown() = loggers.forEach { it.shutdown() }

    fun destroy() {
        this.shutdown()
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)
    }
}