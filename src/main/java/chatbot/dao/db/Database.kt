package chatbot.dao.db

import chatbot.message.LoggableMessage
import chatbot.message.TimeoutMessage
import chatbot.singleton.Config
import org.apache.commons.dbcp2.BasicDataSource

interface Database {
    fun recordMessage(message: LoggableMessage) {}

    fun recordWhisper(message: LoggableMessage) {}

    fun addTimeout(timeout: TimeoutMessage) {}

    fun addNamListTimeout(timeout: TimeoutMessage) {}

}

val postgresInstance: PostgresSource = PostgresSource()
class PostgresSource {
    val ds = BasicDataSource()

    init {
        ds.url = Config.getInstance().postgresUrl
        ds.username = Config.getInstance().postgresUsername
        ds.password = Config.getInstance().postgresPassword
        ds.initialSize = 2
        ds.minIdle = 2
        ds.maxIdle = 20
    }
}


val mariaInstance: MariaSource = MariaSource()
class MariaSource {
    val ds = BasicDataSource()
    init {
        ds.url = Config.getInstance().mariaUrl
        ds.username = Config.getInstance().mariaUsername
        ds.password = Config.getInstance().mariaPassword
        ds.initialSize = 2
        ds.minIdle = 2
        ds.maxIdle = 20
    }
}
