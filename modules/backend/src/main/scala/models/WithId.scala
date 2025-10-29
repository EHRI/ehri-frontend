package models

trait WithId {
  def id: String
  def isA: EntityType.Value

  def idAndType: (EntityType.Value, String) = isA -> id
}
