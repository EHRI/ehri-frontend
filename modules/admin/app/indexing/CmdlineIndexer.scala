package indexing

import play.api.libs.concurrent.Execution.Implicits._
import scala.sys.process._
import defines.EntityType
import play.api.Play.current
import play.api.libs.iteratee.Concurrent
import utils.search.{IndexingError, Indexer}


object CmdlineIndexer {
  val jar = "%s/bin/indexer.jar".format(System.getProperty("user.dir"))
}

/**
 * User: mikebryant
 *
 * Indexer which uses the command-line tool in
 * bin to index items.
 */
case class CmdlineIndexer(chan: Option[Concurrent.Channel[String]] = None, processFunc: String => String = identity[String]) extends Indexer {

  override def withChannel(channel: Concurrent.Channel[String], formatter: String => String)
      = copy(chan = Some(channel), processFunc = formatter)

  /**
   * Process logger which buffers output to `bufferCount` lines
   */
  private val logger = new ProcessLogger {
    val bufferCount = 100 // number of lines to buffer...
    var count = 0
    val errBuffer = collection.mutable.ArrayBuffer.empty[String]

    def buffer[T](f: => T): T = f
    def out(s: => String) = report()
    def err(s: => String) {
      errBuffer += s
      report()
    }

    private def report(): Unit = {
      count += 1
      if (count % bufferCount == 0) {
        chan.map(_.push(processFunc("Items processed: " + count)))
      }
    }
  }

  private val binary = Seq("java", "-jar", CmdlineIndexer.jar)

  private val restUrl = (for {
    host <- current.configuration.getString("neo4j.server.host")
    port <- current.configuration.getInt("neo4j.server.port")
    path <- current.configuration.getString("neo4j.server.endpoint")
  } yield "http://%s:%d/%s".format(host, port, path)).getOrElse(sys.error("Unable to find rest service url"))

  private val solrUrl = current.configuration.getString("solr.path").getOrElse(sys.error("Unable to find solr service url"))

  private val clearArgs = binary ++ Seq(
    "--solr", solrUrl
  )

  private val idxArgs = binary ++ Seq(
    "--index",
    "--solr", solrUrl,
    "--rest", restUrl,
    "--verbose" // print one line of output per item
  )

  private def runProcess(cmd: Seq[String]) {
    play.api.Logger.logger.debug("Index: {}", cmd.mkString(" "))
    val process: Process = cmd.run(logger)
    if (process.exitValue() != 0) {
      throw new IndexingError("Exit code was " + process.exitValue() + "\nLast output: " + logger.errBuffer.mkString("\n"))
    }
  }

  def indexId(id: String): Unit = runProcess(idxArgs ++ Seq("@" + id, "--pretty"))

  def indexTypes(entityTypes: Seq[EntityType.Value]): Unit
        = runProcess(idxArgs ++ entityTypes.map(_.toString))

  def indexChildren(entityType: EntityType.Value, id: String): Unit
      = runProcess(idxArgs ++ Seq("%s|%s".format(entityType, id)))

  def clearAll(): Unit = runProcess(clearArgs ++ Seq("--clear-all"))

  def clearTypes(entityTypes: Seq[EntityType.Value]): Unit
        = runProcess(clearArgs ++ entityTypes.flatMap(s => Seq("--clear-type", s.toString)))

  def clearId(id: String): Unit = runProcess(clearArgs ++ Seq("--clear-id", id))
}
