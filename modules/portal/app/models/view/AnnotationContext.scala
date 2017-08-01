package models.view

import play.api.mvc.QueryStringBindable

/**
 * An enum encoding the different ways in which
 * an annotation may appear in a view:
 *  - block - below an item description
 *  - field - inline with an item field
 *  - list  - in a list of annotations
 */
object AnnotationContext extends Enumeration {
  type Type = Value
  val Block = Value("block")
  val List = Value("list")
  val Field = Value("field")

  implicit val _binder: QueryStringBindable[AnnotationContext.Value] =
    utils.binders.queryStringBinder(this)
}
