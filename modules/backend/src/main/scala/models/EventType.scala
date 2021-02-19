package models

import play.api.libs.json.Format
import utils.EnumUtils

object EventType extends Enumeration {
  type Type = Value
  val
  creation,
  createDependent,
  modification,
  modifyDependent,
  deletion,
  deleteDependent,
  link,
  annotation,
  setGlobalPermissions,
  setItemPermissions,
  setVisibility,
  addGroup,
  removeGroup,
  ingest,
  promotion,
  demotion,
  follow,
  unfollow,
  watch,
  unwatch,
  block,
  unblock = Value

  implicit val _format: Format[EventType.Value] = EnumUtils.enumFormat(this)
}
