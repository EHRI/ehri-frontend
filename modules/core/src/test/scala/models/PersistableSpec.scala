package models

import models.PersistableSpec.testForm
import play.api.data.Form
import play.api.data.Forms._
import play.api.test.PlaySpecification
import services.data.ErrorSet

case class GrandChild(
  a: String
)

case class Child(
  a: String,
  b: Option[Int],
  @models.relation("hasGrandChildren")
  grandChildren: Seq[GrandChild]
) extends Persistable

case class Parent(
  a: String,
  b: Option[Int],
  @models.relation("hasChildren")
  children: Seq[Child]
) extends Persistable

object PersistableSpec {
  val testForm: Form[Parent] = Form(
    mapping(
      "a" -> nonEmptyText,
      "b" -> optional(number(min = 1, max = 10)),
      "children" -> seq(mapping(
        "a" -> nonEmptyText,
        "b" -> optional(number),
        "grandChildren" -> seq(mapping(
          "a" -> nonEmptyText
        )(GrandChild.apply)(GrandChild.unapply))
      )(Child.apply)(Child.unapply))
    )(Parent.apply)(Parent.unapply)
  )
}

class PersistableSpec extends PlaySpecification {
  val data = Parent(
    "test1",
    Some(1),
    Seq(
      Child(
        "test2",
        Some(2),
        Seq(
          GrandChild("test3")
        )
      )
    )
  )

  "turn annotated relations into a map" in {
    Persistable.getRelationToAttributeMap(data) must_== Map(
      "hasChildren" -> "children",
      "hasGrandChildren" -> "grandChildren"
    )
  }

  "maps errors from an ErrorSet to an annotated form model" in {
    val errorSet = ErrorSet(
      errors = Map(),
      relationships = Map(
        "hasChildren" -> Seq(
          Some(
            ErrorSet(
              errors = Map("b" -> Seq("Invalid string")),
              relationships = Map(
                "hasGrandChildren" -> Seq(Some(
                  ErrorSet(errors = Map("a" -> Seq("Invalid format")))
                ))
              )
            )
          )
        )
      )
    )

    val form = testForm.fill(data)
    val res = data.getFormErrors(errorSet, form)
    res.errors.size must_== 2
    res.errors.head.key must_== "children[0].b"
    res.errors.head.messages must contain("Invalid string")
    res.errors.last.key must_== "children[0].grandChildren[0].a"
    res.errors.last.messages must contain("Invalid format")
  }
}
