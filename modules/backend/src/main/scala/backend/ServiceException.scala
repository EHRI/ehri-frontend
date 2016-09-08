package backend

import java.io.IOException

/**
  * Indicates that there was an error attempting to communicate with
  * a service.
  *
  * @param msg   a custom message
  * @param cause the underlying cause
  */
abstract class ServiceException(msg: String, cause: Throwable) extends IOException(msg, cause)
