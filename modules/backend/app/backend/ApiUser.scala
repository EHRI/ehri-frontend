package backend

/**
  * Wrapper for Auth user id string
  */
case class ApiUser(id: Option[String] = None) {
   override def toString = id.getOrElse(None.toString)
 }
