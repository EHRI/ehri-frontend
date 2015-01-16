package utils.search



/**
 * @author Mike Bryant (http://github.com/mikesname)
 */

sealed trait QueryPoint
object Glob extends QueryPoint
case class Point(p: Any) extends QueryPoint

case class QueryRange(points: QueryPoint*) extends QueryPoint
