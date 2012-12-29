package models.base

/**
 * Welcome to an unpleasant package. Case classes that extend the Persistable
 * trait can be mapped to and from a generic json structure used for persisting
 * them on the server. To do this, we have to use reflection (along with the @Relation)
 * annotation that is added to attributes that represent a node -> subnode relationship.
 *
 * This is quite complicated. More complicates is taking a set of field errors that
 * the server gave back, and mapping them to form errors.
 *
 * TODO: Improve all of this drastically.
 */

import defines._
import models.Relation
import models.Entity
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat

object Persistable {
  def getRelationToAttributeMap[T <:Persistable](p: T): Map[String,String] = {
    (Map[String,String]() /: p.getClass.getDeclaredFields) { (a, f) =>
      f.setAccessible(true)
      // If there's a relationship annotation on the value, 
      // add it to the map
      val aa = Option(f.getAnnotation(classOf[Relation])) match {
        case Some(rel) => a + (rel.value -> f.getName)
        case _ => a
      }
      // If the value is a Persistable, add its relations to the map.
      // NB: There is potential for a conflict here if a subtree has
      // sub-items that have the same relation names for different
      // attributes
      f.get(p) match {
        case lst: List[_] => (aa /: lst) { (a, item) =>
          item match {
            // TODO: Check for collisions
            case i: Persistable => aa ++ getRelationToAttributeMap(i)
            case _ => aa
          }
        }
        case _ => aa
      }
    }
  }

  /**
   * Transform a tree-like ErrorSet structure into set of form errors,
   * using a relationship map to translate between relationship names
   * and form mapping attributes, i.e:
   *
   * {
   *   "errors":{},
   *   "relationships":{
   *     "describes":[
   *       {
   *         "errors":{
   *           "name":["No value given for mandatory field"]
   *          },
   *          "relationships":{
   *            "hasAddress":[
   *              {}
   *            ]
   *          }
   *        }
   *      ]
   *    }
   *  }
   *
   * to:
   *
   * descriptions[0].addresses[0]: ["No value given for mandatory field"]
   *
   * The translation of 'describes' -> 'descriptions' and 'hasAddress' -> 'addresses'
   * is inferred by the @Relation annotation on the Persistable case class field.
   */
  def unfurlErrors(        
      errorSet: rest.ErrorSet,
      relmap: Map[String,String],
      currentMap: Map[String,List[String]] = Map(),
      path: Option[String] = None,
      attr: Option[String] = None,
      index: Option[Int] = None): Map[String,List[String]] = {

    // TODO: Tidy this mess up
    val newpath = attr match {
      case Some(s) => index match {
        case Some(i) => path match {
          case Some(p) if p.isEmpty => "%s[%d]".format(s, i)
          case Some(p) => "%s.%s[%d]".format(p, s, i)
          case None => "%s[%d]".format(s, i)
        }
        case _ => ""
      }
      case _ => ""
    }

    // Map the top-level errors
    val nmap = (currentMap /: errorSet.errors.toSeq) { (m, kev) =>
      if (newpath.isEmpty)
        m + (kev._1 -> kev._2)
      else
        m + ("%s.%s".format(newpath, kev._1) -> kev._2)
    }

    // And then the nested relationship errors
    (nmap /: errorSet.relationships) { (m, kev) =>
      val (rel, errorSets) = kev
      val attrName = relmap.getOrElse(rel, sys.error("Unknown error map relationship for %s: %s (%s)".format(this, rel, relmap)))
      (m /: errorSets.zipWithIndex) { (mm, esi) => esi._1 match {
          case Some(errorSet) => unfurlErrors(errorSet, relmap, mm, Some(newpath), Some(attrName), Some(esi._2)) 
          case None => mm
        }
      }
    }
  }
}

/**
 * Base class for `pure` form-backed models that need to be
 * persisted on the server.
 */
trait Persistable {

  import Persistable._

  def id: Option[String]
  def isA: EntityType.Value

  /**
   * Map a tree of errors from the server into form errors.
   * 
   * @type {[type]}
   */
  import play.api.data.FormError
  def errorsToForm(errorSet: rest.ErrorSet): Seq[FormError] = {
    unfurlErrors(errorSet, getRelationToAttributeMap(this)).flatMap { case (field, errorList) =>
      errorList.map(FormError(field, _))
    }.toSeq
  }

  /**
   * Turn the item back into some raw data that can be
   * posted to the rest server.
   */
  def toData: Map[String, Any] = {
    (Map[String, Any]() /: getClass.getDeclaredFields) { (a, f) =>
      f.setAccessible(true)

      f.getName match {
        case s: String if s == Entity.ID => a + (f.getName -> f.get(this))
        case s: String if s == Entity.ISA => a + (Entity.TYPE -> f.get(this).toString)
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
                case date: DateTime => ISODateTimeFormat.date.print(date)
                case enum: Enumeration#Value => enum.toString
                case Some(value) => value match {
                  case date: DateTime => ISODateTimeFormat.date.print(date)
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