package views.p

import models.{UserProfile, Annotation}
import java.net.{MalformedURLException, URL}
import models.base.AnyModel
import defines.{EntityType, PermissionType}
import org.apache.commons.codec.digest.{DigestUtils, Md5Crypt}
import play.api.mvc.Call
import controllers.portal.ReversePortal

/**
 * Portal view helpers.
 */
object Helpers {

  /**
   * Sort a set of annotations into three types.
   * @param annotations A list of annotations
   * @param userOpt An optional user context
   * @return A tuple of annotation sequences: the current user's, promoted, and other
   */
  def sortAnnotations(annotations: Seq[models.Annotation])(
      implicit userOpt: Option[UserProfile]): (Seq[Annotation], Seq[Annotation], Seq[Annotation]) = {
    val (mine,others) = annotations.filterNot(_.isPromoted).partition(_.isOwnedBy(userOpt))
    val promoted = annotations.filter(_.isPromoted)
    (mine, promoted, others)
  }

  def normalizeUrl(s: String): String = {
    try {
      new URL(s).toString
    } catch {
      case e: MalformedURLException if e.getMessage.startsWith("no protocol") => "http://" + s
      case _: MalformedURLException => s
    }
  }

  def isAnnotatable(item: AnyModel, userOpt: Option[models.UserProfile]): Boolean = userOpt.exists { user =>
    item.contentType.exists {
      ct => user.hasPermission(ct, PermissionType.Annotate)
    }
  }

  def linkTo(item: AnyModel): Call = linkTo(item.isA, item.id)

  def linkTo(isA: EntityType.Value, id: String): Call = {
    val portalRoutes: ReversePortal = controllers.portal.routes.Portal
    isA match {
      case EntityType.Country => controllers.portal.routes.Countries.browse(id)
      case EntityType.Concept => controllers.portal.routes.Concepts.browse(id)
      case EntityType.DocumentaryUnit => controllers.portal.routes.DocumentaryUnits.browse(id)
      case EntityType.Repository => controllers.portal.routes.Repositories.browse(id)
      case EntityType.HistoricalAgent => controllers.portal.routes.HistoricalAgents.browse(id)
      case EntityType.UserProfile => controllers.portal.social.routes.Social.userProfile(id)
      case EntityType.Group => controllers.portal.routes.Groups.browse(id)
      case EntityType.Link => controllers.portal.routes.Links.browse(id)
      case EntityType.Annotation => controllers.portal.annotate.routes.Annotations.browse(id)
      case EntityType.Vocabulary => controllers.portal.routes.Vocabularies.browse(id)
      case EntityType.VirtualUnit => controllers.portal.routes.VirtualUnits.browseVirtualCollection(id)
      case _ => Call("GET", "#")
    }
  }

  /**
   * Fetch a gravitar URL for the user, defaulting to the stock picture.
   */
  def gravitar(img: Option[String]): String =
    img.map(_.replaceFirst("https?://", "//"))
      .getOrElse(controllers.portal.routes.PortalAssets.at("img/default-gravitar.png").url)

  def remoteGravitar(userId: String): String = {
    val hash = DigestUtils.md5Hex(s"$userId@ehri-project.eu")
    s"https://secure.gravatar.com/avatar/$hash?d=identicon"
  }


  def virtualUnitUrl(path: Seq[AnyModel], id: String): Call = {
    if (path.isEmpty) controllers.portal.routes.VirtualUnits.browseVirtualCollection(id)
    else controllers.portal.routes.VirtualUnits.browseVirtualUnit(path.map(_.id).mkString(","), id)
  }

  def virtualUnitSearchUrl(path: Seq[AnyModel], id: String): Call = {
    if (path.isEmpty) controllers.portal.routes.VirtualUnits.searchVirtualCollection(id)
    else controllers.portal.routes.VirtualUnits.searchVirtualUnit(path.map(_.id).mkString(","), id)
  }
}
