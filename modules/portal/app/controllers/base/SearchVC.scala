package controllers.base

import backend.rest.cypher.CypherDAO
import play.api.Logger
import play.api.mvc.Controller

import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits._

/**
 * Helpers for searching virtual collections
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait SearchVC {
  this: Controller with ControllerHelpers =>

  import play.api.Play.current

  /**
   * Fetch a list of descendant IDs for a given virtual collection
   * in order to constrain a search space. This is:
   *  - child virtual collections
   *  - top level documentary units
   *
   * @param id the parent VC id
   * @return a sequence of descendant IDs
   */
  protected def descendantIds(id: String): Future[Seq[String]] = {
    import play.api.libs.json._
    val dao = new CypherDAO()

    val reader: Reads[Seq[String]] =
      (__ \ "data").read[Seq[Seq[Seq[String]]]]
        .map { r => r.flatten.flatten }

    dao.get[Seq[String]](
      """
        |START vc = node:entities(__ID__ = {vcid})
        |MATCH vc<-[?:isPartOf*]-child,
        |      ddoc<-[?:includesUnit]-vc,
        |      doc<-[?:includesUnit]-child
        |RETURN DISTINCT collect(DISTINCT child.__ID__) + collect(DISTINCT doc.__ID__) + collect(DISTINCT ddoc.__ID__)
      """.stripMargin, Map("vcid" -> play.api.libs.json.JsString(id)))(reader).map { seq =>
      Logger.debug(s"Elements: ${seq.length}, distinct: ${seq.distinct.length}")

      current.configuration.getInt("search.vc.maxDescendants").map { vcLimit =>
        if (seq.length > vcLimit) {
          Logger.error(s"Truncating clauses on child item search for $id: items ${seq.length}")
          seq.distinct.take(vcLimit)
        } else seq
      }.getOrElse(seq)
    }
  }
}
