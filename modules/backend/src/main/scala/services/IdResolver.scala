package services

import scala.concurrent.Future


trait IdResolver {
  /**
   * Fetch an item of any type.
   */
  def getAny[MT](id: String)(implicit apiUser: ApiUser,  rd: Readable[MT]): Future[MT]
}

