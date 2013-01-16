package models.forms

import play.api.data._
import play.api.data.Forms._

import models._
import base.Persistable
import defines.EntityType
import play.api.libs.json.Json
import defines.EnumWriter.enumWrites


object GroupF {

  final val BELONGS_REL = "belongsTo"
}

case class GroupF(
  val id: Option[String],
  val identifier: String,
  val name: String) extends Persistable {
  val isA = EntityType.Group

  import Entity._
  def toJson = Json.obj(
    ID -> ID,
    TYPE -> isA,
    DATA -> Json.obj(
      IDENTIFIER -> identifier,
      "name" -> name
    )
  )
}


object GroupForm {

  val form = Form(
      mapping(
    		Entity.ID -> optional(text),
    		Entity.IDENTIFIER -> nonEmptyText,
    		"name" -> nonEmptyText
      )(GroupF.apply)(GroupF.unapply)
  ) 
}
