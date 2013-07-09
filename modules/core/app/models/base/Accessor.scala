package models.base

import defines.EntityType
import models._
import play.api.libs.json._
import models.json.{ClientConvertable, RestReadable}

object Accessor {
  final val BELONGS_REL = "belongsTo"

  implicit object Converter extends RestReadable[Accessor] with ClientConvertable[Accessor] {
    implicit val restReads: Reads[Accessor] = new Reads[Accessor] {
      def reads(json: JsValue): JsResult[Accessor] = {
        json.validate[Accessor](AnyModel.Converter.restReads.asInstanceOf[Reads[Accessor]])
      }
    }

    implicit val clientFormat: Format[Accessor] = new Format[Accessor] {
      def reads(json: JsValue): JsResult[Accessor] = {
        json.validate[Accessor](AnyModel.Converter.clientFormat.asInstanceOf[Format[Accessor]])
      }
      def writes(a: Accessor): JsValue = {
        Json.toJson(a)(AnyModel.Converter.clientFormat.asInstanceOf[Format[Accessor]])
      }
    }
  }
}

trait Accessor extends AnyModel {
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