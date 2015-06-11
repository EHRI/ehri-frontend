
package object defines {
  import language.implicitConversions
  implicit def enumToString(e: Enumeration#Value): String = e.toString
}
