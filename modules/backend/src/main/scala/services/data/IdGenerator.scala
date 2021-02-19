package services.data

import models.EntityType

import scala.concurrent.{ExecutionContext, Future}

/**
 * Helper that generates IDs for content types.
 */
trait IdGenerator {

  /**
   * Get the next ID for the given entity type by incrementing
   * the highest existing numerical ID by 1.
   */
  def getNextNumericIdentifier(entityType: EntityType.Value, pattern: String)(implicit executionContent: ExecutionContext): Future[String]

  /**
   * Get the next ID for the given entity type with the given
   * parent as a permission scope, by incrementing the highest
   * existing numerical ID by 1.
   */
  def getNextChildNumericIdentifier(parentId: String, entityType: EntityType.Value, pattern: String)(implicit executionContent: ExecutionContext): Future[String]
}

object IdGenerator {
  def safeInt(s : String) : Option[Int] = try {
    Some(s.toInt)
  } catch {
    case _ : java.lang.NumberFormatException => None
  }

  def nextNumericId(ids: Seq[String]): Int = ids.flatMap { rid =>
    rid.split("\\D+").filterNot(_ == "").headOption.flatMap(safeInt)
  }.padTo(1, 0).max + 1 // ensure we get '1' with an empty list
}

