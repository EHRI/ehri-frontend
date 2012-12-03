package models.base

import defines._
import models.Relation
import models.Entity

/**
 * Base class for `pure` form-backed models that need to be
 * persisted on the server.
 */
trait Persistable {

  def id: Option[String]
  def isA: EntityType.Value

  /**
   * Turn the item back into some raw data that can be
   * posted to the rest server.
   */
  def toData: Map[String, Any] = {

    (Map[String, Any]() /: getClass.getDeclaredFields) { (a, f) =>
      f.setAccessible(true)

      f.getName match {
        case s: String if s == Entity.ID => a + (f.getName -> f.get(this))
        case s: String if s == "isA" => a + (Entity.TYPE -> f.get(this).toString)
        case _ => {
          // Handle relations...
          Option(f.getAnnotation(classOf[Relation])) match {
            case Some(rel) => {
              val relmap = a.getOrElse(Entity.RELATIONSHIPS, Map[String, Any]()).asInstanceOf[Map[String, Any]]
              val rellst = f.get(this) match {
                case lst: List[_] => lst.flatMap { i =>
                  i match {
                    case i: Persistable => List(i.toData)
                    case _ => Nil
                  }
                }
                case sng: Persistable => List(sng).map(_.toData)
                case _ => Nil
              }
              a + (Entity.RELATIONSHIPS -> (relmap + (rel.value -> rellst)))
            }
            // Handle data attributes...
            case None => {
              val datamap: Map[String, Any] = a.getOrElse(Entity.DATA, Map()).asInstanceOf[Map[String, Any]]
              val value = f.get(this) match {
                // TODO: Handle nested case classes, i.e. sub-parts of objects.
                case None => None
                case enum: Enumeration#Value => enum.toString
                case Some(value) => value match {
                  case enum: Enumeration#Value => enum.toString
                  case x => x
                }
                case x => x
              }

              // Handle nested case classes
              value match {
                case as: AttributeSet => a + (Entity.DATA -> (datamap ++ as.toData))
                case _ => a + (Entity.DATA -> (datamap + (f.getName -> value)))
              }
            }
          }
        }
      }
    }
  }
}