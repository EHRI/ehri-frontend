package models

import play.api.libs.json.Format
import utils.EnumUtils


object PermissionType extends Enumeration {
  type Type = Value
  val Create = Value("create")
  val Update = Value("update")
  val Delete = Value("delete")
  val Owner = Value("owner")
  val Grant = Value("grant")
  val Annotate = Value("annotate")
  val Promote = Value("promote")

  /**
    * Unfortunately Scala enums can't have proper
    * methods/data so we have to use this hacky function
    * instead...
    */
  private def bitMask(p: Value): Int = p match {
    case Create => 1
    case Update => 2
    case Delete => 4
    case Annotate => 8
    case Owner => 15 // C,U,D,A
    case Grant => 16
    case Promote => 32
  }

  /**
    * Test permission p2 is 'in' permission p1
    */
  def in(p1: Value, p2: Value): Boolean = (bitMask(p1) & bitMask(p2)) == bitMask(p2)

  implicit val _format: Format[PermissionType.Value] = EnumUtils.enumFormat(this)
}
