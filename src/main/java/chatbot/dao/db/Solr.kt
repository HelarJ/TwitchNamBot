package chatbot.dao.db

import chatbot.message.LoggableMessage
import chatbot.singleton.Config
import chatbot.utils.log
import org.apache.solr.client.solrj.SolrServerException
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient.RemoteSolrException
import org.apache.solr.client.solrj.impl.Http2SolrClient
import org.apache.solr.common.SolrInputDocument
import java.io.IOException
import java.time.Instant
import java.time.temporal.ChronoUnit

class Solr : Database {
    private var lastCommit: Instant = Instant.now()
    private var commitBacklog: MutableList<SolrInputDocument> = mutableListOf()

    private fun getConn(): Http2SolrClient = Http2SolrClient.Builder(Config.getInstance().solrCredentials).build()

    override fun recordMessage(message: LoggableMessage) {

        val document = SolrInputDocument()
        document.addField("id", message.uuid)
        document.addField("time", message.instant.toString())
        document.addField("username", message.sender)
        document.addField("message", message.stringMessage)
        commitBacklog.add(document)


        //Batch inserting as single inserts comes with very heavy disk usage.
        if (lastCommit.plus(10, ChronoUnit.SECONDS).isBefore(Instant.now())) {
            try {
                getConn().use { solr ->
                    solr.add(commitBacklog)
                    solr.commit()
                    commitBacklog.clear()
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

}