package services.rest

import services.ServiceException

case class ServiceOffline(msg: String, cause: Throwable) extends ServiceException(msg, cause)