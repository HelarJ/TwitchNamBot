package chatbot.utils

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.sql.SQLException

inline val <reified T> T.log: Logger get() = LogManager.getLogger(T::class.java)

inline fun <reified T> T.warnSql(e: SQLException) =
    LogManager.getLogger(T::class.java).warn("SQLException: ${e.message} VendorError: ${e.errorCode}")

inline fun <reified T> T.errorSql(e: SQLException) =
    LogManager.getLogger(T::class.java).error("SQLException: ${e.message} VendorError: ${e.errorCode}")