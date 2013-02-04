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

  val NAME = "name"
  val DESCRIPTION = "description"
}

case class GroupF(
  val id: Option[String],
  val identifier: String,
  val name: String,
  val description: Option[String] = None
) extends Persistable {
  val isA = EntityType.Group

  import Entity._

  def toJson = Json.obj(
    ID -> id,
    TYPE -> isA,
    DATA -> Json.obj(
      IDENTIFIER -> identifier,
      GroupF.NAME -> name,
      GroupF.DESCRIPTION -> description
    )
  )
}


object GroupForm {

  val form = Form(
    mapping(
      Entity.ID -> optional(text),
      Entity.IDENTIFIER -> nonEmptyText,
      GroupF.NAME -> nonEmptyText,
      GroupF.DESCRIPTION -> optional(nonEmptyText)
    )(GroupF.apply)(GroupF.unapply)
  )
}
