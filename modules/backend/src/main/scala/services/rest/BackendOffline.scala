package services.rest

import services.ServiceException

case class BackendOffline(msg: String, cause: Throwable) extends ServiceException(msg, cause)