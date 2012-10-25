package models

import defines.PublicationStatus

/**
 * This type alias provides a means to ensure that the
 * @Relation(type) annotation on constructor val params
 * is copied to the field value, as described here:
 *
 * http://www.scala-lang.org/api/current/index.html#scala.annotation.target.package
 *
 * See also:
 *
 * http://stackoverflow.com/questions/11853878/getannotations-on-scala-class-fields
 */
object Annotations {
  type Relation = models.Relation @scala.annotation.target.field
}

/**
 * Base class for Entity-backed models.
 */
trait BaseModel {
  def id: Option[Long]
  def isA: EntityTypes.Value

  /**
   * Turn the item back into some raw data that can be
   * posted to the rest server.
   */
  def toData: Map[String, Any] = {

    (Map[String, Any]() /: getClass.getDeclaredFields) { (a, f) =>
      f.setAccessible(true)

      if (f.getName == "id") {
        a + (f.getName -> f.get(this))
      } else {
        // Handle relations...
        val rel = f.getAnnotation(classOf[Relation])
        if (rel != null) {
          val relmap = a.getOrElse("relationships", Map[String, Any]()).asInstanceOf[Map[String, Any]]
          val rellst = f.get(this).asInstanceOf[List[BaseModel]].map(_.toData)
          a + ("relationships" -> (relmap + (rel.value -> rellst)))
        } else {
          val datamap: Map[String, Any] = a.getOrElse("data", Map()).asInstanceOf[Map[String, Any]]
          val value = f.get(this) match {
            case None => None
            // Special case for entity type... hopefully we can make this go away...
            case isa: EntityTypes.Value => isa.asInstanceOf[EntityTypes.Value].toString
            case Some(value) => value match {
              case pub: PublicationStatus.Value => pub.asInstanceOf[PublicationStatus.Value].toString              
              case x => x
            }
            case x => x
          }

          a + ("data" -> (datamap + (f.getName -> value)))
        }
      }
    }
  }

  def toEntity = {

  }
}