package chatbot.dao.db

import chatbot.message.LoggableMessage
import chatbot.singleton.Config
import chatbot.singleton.SharedState
import chatbot.utils.log
import org.apache.solr.client.solrj.SolrClient
import org.apache.solr.client.solrj.SolrServerException
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient.RemoteSolrException
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient
import org.apache.solr.common.SolrInputDocument
import java.io.IOException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

private val queue: LinkedBlockingQueue<LoggableMessage> = LinkedBlockingQueue()
private val commitBacklog: LinkedBlockingQueue<SolrInputDocument> = LinkedBlockingQueue()

class Solr : Database, Runnable {
    private var running: Boolean = true
    private var lastCommit: Instant = Instant.now()

    private val executor = Executors.newVirtualThreadPerTaskExecutor()

    private fun getConn(): SolrClient = HttpJdkSolrClient.Builder(Config.getSolrCredentials())
        .withConnectionTimeout(5, TimeUnit.SECONDS)
        .withRequestTimeout(10, TimeUnit.SECONDS)
        .withExecutor(executor)
        .build()


    override fun recordMessage(message: LoggableMessage) {
        queue.add(message)
    }

    override fun run() {
        log.info("Started ${this.javaClass}")

        while (SharedState.getInstance().isBotStillRunning && !Thread.currentThread().isInterrupted && running) {
            queue.poll(1, TimeUnit.SECONDS)?.let {
                val document = SolrInputDocument()
                document.addField("id", it.uuid)
                document.addField("time", it.instant.toString())
                document.addField("username", it.sender)
                document.addField("message", it.stringMessage)

                commitBacklog.add(document)
            }

            // single inserts are not good for solr, need to do them in batches.
            if (commitBacklog.isNotEmpty() && lastCommit.plus(10, ChronoUnit.SECONDS).isBefore(Instant.now())) {
                try {
                    val conn = getConn()
                    conn.use { solr ->
                        var toAdd = ArrayList<SolrInputDocument>()
                        toAdd.addAll(commitBacklog)
                        var add = solr.add(toAdd, 1000)
                        log.debug("Update took ${add.elapsedTime}ms, added ${toAdd.size}")
                        commitBacklog.removeAll(toAdd)
                    }
                } catch (e: IOException) {
                    log.warn("Solr error: ${e.message}")
                } catch (e: SolrServerException) {
                    log.warn("Solr error: ${e.message}")
                } catch (e: RemoteSolrException) {
                    log.warn("Solr error: ${e.message}")
                } finally {
                    lastCommit = Instant.now()
                }
            }
        }

        log.info("Shut down ${this.javaClass}")
    }

    override fun shutdown() {
        running = false
    }
}