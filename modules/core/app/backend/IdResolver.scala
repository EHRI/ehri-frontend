package backend

import scala.concurrent.Future


trait IdResolver {
  /**
   * Fetch an item of any type.
   */
  def getAny[MT](id: String)(implicit apiUser: ApiUser,  rd: BackendReadable[MT]): Future[MT]
}

