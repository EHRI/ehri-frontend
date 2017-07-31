package services

case class ServiceOffline(msg: String, cause: Throwable) extends ServiceException(msg, cause)