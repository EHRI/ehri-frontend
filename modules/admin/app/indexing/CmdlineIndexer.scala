package indexing

import play.api.libs.concurrent.Execution.Implicits._
import scala.sys.process._
import scala.concurrent.Future
import defines.EntityType
import play.api.Play.current

/**
 * User: mikebryant
 *
 * Indexer which uses the command-line tool in
 * bin to index items.
 */
case class CmdlineIndexer() extends NewIndexer {

  private val binary = Seq("java", "-jar", "%s/bin/indexer.jar".format(System.getProperty("user.dir")))

  private val restUrl = (for {
    host <- current.configuration.getString("neo4j.server.host")
    port <- current.configuration.getInt("neo4j.server.port")
    path <- current.configuration.getString("neo4j.server.endpoint")
  } yield "http://%s:%d/%s".format(host, port, path)).getOrElse(sys.error("Unable to find rest service url"))

  private val solrUrl = current.configuration.getString("solr.path").getOrElse(sys.error("Unable to find solr service url"))

  private val idxArgs = binary ++ Seq(
    "--index",
    "--solr", solrUrl,
    "--rest", restUrl,
    "--verbose" // print one line of output per item
  )

  private def runIndexWithArgs(args: Seq[String]): Stream[String]
        = {
    val cmd = idxArgs ++ args
    play.api.Logger.logger.debug("Cmdline Indexer: " + cmd.mkString(" "))
    cmd.lines
  }

  private def runIndexWithArgs(args: Seq[String], log: ProcessLogger): Stream[String]
  = {
    val cmd = idxArgs ++ args
    play.api.Logger.logger.debug("Cmdline Indexer: " + cmd.mkString(" "))
    cmd.lines(log)
  }

  private def runExpectingNoOutput(cmd: Seq[String]): Option[String] = {
    val lines = cmd.lines
    lines.length match {
      case 0 => None
      case _ => Some(lines.mkString("\n"))
    }
  }

  // When all updates have finished, commit the results
  case class Logger(cb: String => Unit) extends ProcessLogger {
    var count = 0
    def buffer[T](f: => T): T = f
    def out(s: => String) {}
    def err(s: => String) {
      count += 1
      cb.apply(s)
    }
  }



  /**
   * Index a single item by id
   * @param id
   * @return
   */
  def indexId(id: String): Future[Option[String]] = Future {
    val lines = runIndexWithArgs(Seq("@" + id, "--pretty"))
    /*lines.length match {
      case 1 if lines.head.contains(id) => None // Okay!
      case 0 => Some("Indexer gave no output - expecting one line matching item id.")
      case _ => Some(lines.mkString("\n")) // Return the error
    }*/
    None
  }

  /**
   * Index all items of a given type
   * @param entityTypes
   * @return
   */
  def indexTypes(entityTypes: Seq[EntityType.Value]): Future[Stream[String]]
        = Future(runIndexWithArgs(entityTypes.map(_.toString)))

  /**
   * Index all items of a given type
   * @param entityTypes
   * @return
   */
  def indexTypes(entityTypes: Seq[EntityType.Value], cb: String => Unit): Future[Stream[String]]
        = Future(runIndexWithArgs(entityTypes.map(_.toString), Logger(cb)))

  /**
   * Index all children of a given item.
   * @param entityType
   * @param id
   * @return
   */
  def indexChildren(entityType: EntityType.Value, id: String): Future[Stream[String]]
        = Future(runIndexWithArgs(Seq("%s|%s".format(entityType, id))))

  /**
   * Index all children of a given item.
   * @param entityType
   * @param id
   * @return
   */
  def indexChildren(entityType: EntityType.Value, id: String, cb: String => Unit): Future[Stream[String]]
        = Future(runIndexWithArgs(Seq("%s|%s".format(entityType, id)), Logger(cb)))

  /**
   * Clear the index of all items.
   * @return
   */
  def clearAll: Future[Option[String]] = Future(runExpectingNoOutput(binary ++ Seq("--clear-all")))

  /**
   * Clear the index of all items of a given type.
   * @param entityTypes
   * @return
   */
  def clearTypes(entityTypes: Seq[EntityType.Value]): Future[Option[String]]
        = Future(runExpectingNoOutput(binary ++ entityTypes.flatMap(s => Seq("--clear-type", s.toString))))

  /**
   * Clear a given item from the index.
   * @param id
   * @return
   */
  def clearId(id: String): Future[Option[String]]
        = Future(runExpectingNoOutput(binary ++ Seq("--clear-id", id)))
}
