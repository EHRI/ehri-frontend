package views.p

import models.{UserProfile, Annotation}
import java.net.{MalformedURLException, URL}
import models.base.AnyModel
import defines.PermissionType

object AnnotationViewType extends Enumeration {
  type Type = Value
  /**
   * Annotations which a user made, and are thus
   * most immediately interesting.
   */
  val Mine = Value("mine")

  /**
   * Annotations that have been promoted, and
   * are this deemed slightly less interesting.
   */
  val Promoted = Value("promoted")

  /**
   * Annotations made by others and visible to
   * a user, which may be less interesting.
   */
  val Other = Value("other")
}

/**
 * Portal view helpers.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
object Helpers {

  /**
   * Sort a set of annotations into three types.
   * @param annotations A list of annotations
   * @param userOpt An optional user context
   * @return A map of AnnotationViewTypes to Annotations
   */
  def sortAnnotations(annotations: Seq[Annotation])(
      implicit userOpt: Option[UserProfile]): Map[AnnotationViewType.Value, Seq[Annotation]] = {
    import AnnotationViewType._
    val (mine,others) = annotations.filterNot(_.isPromoted).partition(_.isOwnedBy(userOpt))
    val promoted = annotations.filter(_.isPromoted)
    Map(Mine -> mine, Promoted -> promoted, Other -> others)
  }

  def normalizeUrl(s: String): String = {
    try {
      new URL(s).toString
    } catch {
      case e: MalformedURLException if e.getMessage.startsWith("no protocol") => "http://" + s
      case _: MalformedURLException => s
    }
  }

  def isAnnotatable(item: AnyModel, userOpt: Option[UserProfile]) = userOpt.exists { user =>
    item.contentType.exists {
      ct => user.hasPermission(ct, PermissionType.Annotate)
    }
  }
}
