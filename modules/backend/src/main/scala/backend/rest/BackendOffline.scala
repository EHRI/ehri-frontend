package backend.rest

import backend.ServiceException

case class BackendOffline(msg: String, cause: Throwable) extends ServiceException(msg, cause)