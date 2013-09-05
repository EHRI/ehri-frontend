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

  private val binary = "%s/bin/indexer.jar".format(System.getProperty("user.dir"))

  private val restUrl = (for {
    host <- current.configuration.getString("neo4j.server.host")
    port <- current.configuration.getInt("neo4j.server.port")
    path <- current.configuration.getString("neo4j.server.endpoint")
  } yield "http://%s:%d/%s".format(host, port, path)).getOrElse(sys.error("Unable to find rest service url"))

  private val solrUrl = current.configuration.getString("solr.path").getOrElse(sys.error("Unable to find solr service url"))

  private val baseArgs = Seq(binary, "--index", "--solr", solrUrl, "--rest", restUrl, "--pretty")

  private def runCmdWithArgs(args: Seq[String]): Future[Stream[String]] = Future((baseArgs ++ args).lines)

  /**
   * Index a single item by id
   * @param id
   * @return
   */
  def indexId(id: String): Future[Option[Error]] = ???

  /**
   * Index all items of a given type
   * @param entityType
   * @return
   */
  def indexType(entityType: EntityType.Value): Future[Stream[String]] = ???

  /**
   * Index all children of a given item.
   * @param entityType
   * @param id
   * @return
   */
  def indexChildren(entityType: EntityType.Value, id: String): Future[Stream[String]] = ???

  /**
   * Clear the index of all items.
   * @return
   */
  def clearAll: Future[Option[Error]] = ???

  /**
   * Clear the index of all items of a given type.
   * @param entityType
   * @return
   */
  def clearType(entityType: EntityType.Value): Future[Option[Error]] = ???

  /**
   * Clear a given item from the index.
   * @param id
   * @return
   */
  def clearId(id: String): Future[Option[Error]] = ???
}
