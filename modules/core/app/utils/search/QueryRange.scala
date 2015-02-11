package utils.search



/**
 * @author Mike Bryant (http://github.com/mikesname)
 */

sealed trait QueryPoint {
  def to(other: QueryPoint): QueryRange = QueryRange(this, other)
}
object Start extends QueryPoint
object End extends QueryPoint
case class Val(p: Any) extends QueryPoint
case class QueryRange(points: QueryPoint*) extends QueryPoint
