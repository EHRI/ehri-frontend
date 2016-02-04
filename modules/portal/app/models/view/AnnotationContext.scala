package models.view

import defines.BindableEnum

/**
 * An enum encoding the different ways in which
 * an annotation may appear in a view:
 *  - block - below an item description
 *  - field - inline with an item field
 *  - list  - in a list of annotations
 */
object AnnotationContext extends BindableEnum {
  type Type = Value
  val Block = Value("block")
  val List = Value("list")
  val Field = Value("field")
}
