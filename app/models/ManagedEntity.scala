package models

trait ManagedEntityBuilder[T <: BaseModel] {
  def apply(e: AccessibleEntity): T
}

trait ManagedEntity extends BaseModel {
  def identifier: String
}