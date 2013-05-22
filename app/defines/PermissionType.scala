package defines

object PermissionType extends Enumeration {
	type Type = Value
	val Create = Value("create")
	val Update = Value("update")
	val Delete = Value("delete")
	val Owner = Value("owner")
	val Grant = Value("grant")
	val Annotate = Value("annotate")

  /**
   * Unfortunately Scala enums can't have proper
   * methods/data so we have to use this hacky function
   * instead...
   * @param p
   * @return
   */
  private def bitMask(p: Value): Int = p match {
    case Create => 1
    case Update => 2
    case Delete => 4
    case Annotate => 8
    case Owner => 15 // C,U,D,A
    case Grant => 16
  }

  /**
   * Test permission p2 is 'in' permission p1
   * @param p1
   * @param p2
   * @return
   */
  def in(p1: Value, p2: Value): Boolean = (bitMask(p1) & bitMask(p2)) == bitMask(p2)
}