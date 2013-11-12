package backend

import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import models.Entity

case class ErrorSet(
  errors: Map[String,List[String]],
  relationships: Map[String,List[Option[ErrorSet]]]
) {
  /**
   * Given a persistable class, unfurl the nested errors so that they
   * conform to members of this class's form fields.
   */
  def errorsFor: Map[String,List[String]] = {
    // TODO: Handle nested errors
    errors
  }
}

/**
  * Structure that holds a set of errors for an entity and its
  * subtree relations.
  *
  */
object ErrorSet {
  implicit val errorReads: Reads[ErrorSet] = (
    (__ \ "errors").lazyRead(map[List[String]]) and
    (__ \ Entity.RELATIONSHIPS).lazyRead(map[List[Option[ErrorSet]]](list(optionNoError(errorReads))))
  )(ErrorSet.apply _)
 }
