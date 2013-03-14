package models.base

trait Formable[T] {
  def formable: T
  def formableOpt: Option[T]
}