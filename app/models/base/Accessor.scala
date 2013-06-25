package models.base

import defines.EntityType
import models._
import play.api.data.validation.ValidationError
import play.api.libs.json._
import models.Group
import scala.Some
import play.api.data.validation.ValidationError
import models.Group
import play.api.libs.json.KeyPathNode
import scala.Some
import play.api.data.validation.ValidationError
import models.json.{ClientConvertable, RestReadable}

object Accessor {
  final val BELONGS_REL = "belongsTo"

  implicit object Converter extends RestReadable[Accessor] with ClientConvertable[Accessor] {
    implicit val restReads = new Reads[Accessor] {
      def reads(json: JsValue): JsResult[Accessor] = {
        (json \ "type").as[EntityType.Value](defines.EnumUtils.enumReads(EntityType)) match {
          case EntityType.Group => json.validate[GroupMeta](GroupMeta.Converter.restReads)
          case EntityType.UserProfile => json.validate[UserProfileMeta](UserProfileMeta.Converter.restReads)
          case t => JsError(JsPath(List(KeyPathNode("type"))), ValidationError("Unexpected meta-model for accessor type: " + t))
        }
      }
    }

    implicit val clientFormat = new Format[Accessor] {
      def reads(json: JsValue): JsResult[Accessor] = {
        (json \ "type").as[EntityType.Value](defines.EnumUtils.enumReads(EntityType)) match {
          case EntityType.Group => json.validate[GroupMeta](GroupMeta.Converter.clientFormat)
          case EntityType.UserProfile => json.validate[UserProfileMeta](UserProfileMeta.Converter.clientFormat)
          case t => JsError(JsPath(List(KeyPathNode("type"))), ValidationError("Unexpected meta-model for accessor type: " + t))
        }
      }
      def writes(a: Accessor): JsValue = {
        a match {
          case up: UserProfileMeta => Json.toJson(up)(UserProfileMeta.Converter.clientFormat)
          case g: GroupMeta => Json.toJson(g)(GroupMeta.Converter.clientFormat)
          case t => sys.error("Unexcepted type for accessor client conversion: " + t)
        }
      }
    }
  }
}

trait Accessor extends MetaModel[_] {
  val groups: List[GroupMeta]
  val id: String
  val isA: EntityType.Value

  lazy val allGroups: List[GroupMeta] = getGroups(this)

  def isAdmin = getAccessor(groups, "admin").isDefined

	// Search up the tree(?) if parent groups, looking
	// for one with the desired id.
	def getAccessor(groups: List[Accessor], id: String): Option[Accessor] = {
	  groups match {
	    case lst @ head :: rest => {
	      if (head.id == id) Some(head)	        
	      else getAccessor(head.groups, id) match {
	          case s @ Some(g) => s
	          case None => getAccessor(rest, id)
	      }
	    }
	    case Nil => None
	  }
	}

  private def getGroups(acc: Accessor): List[GroupMeta] = {
    acc.groups.foldLeft(acc.groups) { case (all, g) =>
      all ++ getGroups(g)
    }.distinct
  }
}