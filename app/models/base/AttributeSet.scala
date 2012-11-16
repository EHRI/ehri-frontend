package models.base

/**
 * Base trait for a set of attributes that for part
 * of a model class, but does not form a standalone
 * entity in its own right.
 */
trait AttributeSet {
  def toData: Map[String,Any] = (Map[String, Any]() /: getClass.getDeclaredFields) {(a, f) =>
    f.setAccessible(true)
    a + (f.getName -> toValue(f.get(this)))
  }
  
  def toValue(v: Any): Any = v match {
      case Some(value) => value
      case None => None
    }
}