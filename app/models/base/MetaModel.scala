package models.base

import play.api.libs.json.JsObject
import defines.EntityType

trait Model {
  val id: Option[String]
  val isA: EntityType.Value
}

/**
 * Created by mike on 23/06/13.
 */
trait MetaModel[T <: Model] {
  val model: T
}

trait Hierarchical[+T] {
  val parent: Option[Hierarchical[T]]

  def ancestors: List[Hierarchical[T]] = {
    parent.map(p => p :: p.ancestors) getOrElse List.empty
  }
}