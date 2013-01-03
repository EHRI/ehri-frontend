package models.base

import models.Entity

/**
 * Base trait for entities that 'extend' a plain Entity.
 */
trait WrappedEntity {
  
  val e: Entity
  
  // Proxy methods - TODO: Reduce the need for these?
  def id = e.id
  def isA = e.isA
  def stringProperty(name: String) = e.stringProperty(name)
  def listProperty(name: String) = e.listProperty(name)
  def property(name: String) = e.property(name)
  def data = e.data
}