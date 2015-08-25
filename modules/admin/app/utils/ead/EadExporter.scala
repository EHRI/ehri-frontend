package utils.ead

import com.jmcejuela.scala.xml.XMLPrettyPrinter
import play.api.i18n.Messages
import play.api.libs.concurrent.Execution.Implicits._
import backend.{BackendHandle, ApiUser}
import scala.concurrent.Future
import utils.PageParams
import models.{Repository, DocumentaryUnit}

/**
 * Class which interacts with the backend to create an EAD document
 * given a single top-level collection ID.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class EadExporter(backendHandle: BackendHandle)(implicit apiUser: ApiUser) {
  private val params = PageParams.empty.withoutLimit // can't get around large limits yet...
  private val printer = new XMLPrettyPrinter(4)

  /**
   * Fetch the full item and it's set of children, recursively.
   */
  private def fetchTree(id: String, eadId: String): Future[DocTree] = for {
    doc <- backendHandle.get[DocumentaryUnit](id)
    children <- backendHandle.listChildren[DocumentaryUnit,DocumentaryUnit](id, params)
    trees <- Future.sequence(children.map(c => {
      if (c.childCount.getOrElse(0) > 0) fetchTree(c.id, eadId)
      else Future.successful(DocTree(eadId, c, Seq.empty))
    }))
  } yield DocTree(eadId, doc, trees)

  // Ugh, need to fetch the repository manually to
  // ensure we have detailed address info. Otherwise we'll
  // only get mandatory fields and relations.
  private def fetchRepository(rid: Option[String]): Future[Option[Repository]] = rid.map { id =>
    backendHandle.get[Repository](id).map(r => Some(r))
  } getOrElse {
    Future.successful(Option.empty[Repository])
  }

  /**
   * Fetch an EAD document.
   */
  def exportEad(id: String, eadId: String)(implicit messages: Messages): Future[String] = for {
    doc <- backendHandle.get[DocumentaryUnit](id)
    repository <- fetchRepository(doc.holder.map(_.id))
    tree <- fetchTree(id, eadId)
    treeWithRepo = tree.copy(item = tree.item.copy(holder = repository))
  } yield views.xml.export.ead.ead(treeWithRepo).body
}
