package models.base

/**
 * Welcome to an unpleasant package. Case classes that extend the Persistable
 * trait can be mapped to and from a generic json structure used for persisting
 * them on the server. To do this, we have to use reflection (along with the @Relation)
 * annotation that is added to attributes that represent a node -> subnode relationship.
 *
 * This is quite complicated. More complicated is taking a set of field errors that
 * the server gave back, and mapping them to form errors.
 *
 * TODO: Improve all of this drastically.
 */

import defines._
import models.Relation
import backend.ErrorSet


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
      errorSet: ErrorSet,
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
          case Some(es) => unfurlErrors(es, relmap, mm, Some(newpath), Some(attrName), Some(esi._2))
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
  import play.api.data.FormError
  import play.api.data.Form

  def id: Option[String]
  def isA: EntityType.Value

  def getFormErrors[T](errorSet: ErrorSet, form: Form[T]): Form[T] = {
    val serverErrors: Seq[FormError] = errorsToForm(errorSet)
    form.copy(errors = form.errors ++ serverErrors)
  }


  /**
   * Map a tree of errors from the server into form errors.
   */
  private def errorsToForm(errorSet: ErrorSet): Seq[FormError] = {
    unfurlErrors(errorSet, getRelationToAttributeMap(this)).flatMap { case (field, errorList) =>
      errorList.map(FormError(field, _))
    }.toSeq
  }
}