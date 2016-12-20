package backend

import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.libs.functional.syntax._

case class ErrorSet(
  errors: Map[String,Seq[String]],
  relationships: Map[String,Seq[Option[ErrorSet]]]
) {
  /**
   * Given a persistable class, unfurl the nested errors so that they
   * conform to members of this class's form fields.
   */
  def errorsFor: Map[String,Seq[String]] = {
    // TODO: Handle nested errors
    errors
  }

  override def toString: String = errors.toString() // TODO: Handle nested errors
}

/**
  * Structure that holds a set of errors for an entity and its
  * subtree relations.
  *
  */
object ErrorSet {
  implicit val errorReads: Reads[ErrorSet] = (
    (__ \ "errors").lazyRead(map[Seq[String]]) and
    (__ \ Entity.RELATIONSHIPS).lazyRead(map[Seq[Option[ErrorSet]]](seq(optionNoError(errorReads))))
  )(ErrorSet.apply _)
 }
