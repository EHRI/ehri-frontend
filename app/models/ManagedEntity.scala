package models

trait ManagedEntity extends BaseModel {
  def identifier: String
}