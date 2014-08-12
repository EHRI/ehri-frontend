package backend

import models.json.RestReadable
import scala.concurrent.Future

trait IdResolver {
  /**
   * Fetch an item of any type.
   */
  def getAny[MT](id: String)(implicit apiUser: ApiUser,  rd: RestReadable[MT]): Future[MT]
}

