package auth

import models.OAuth2Association
import scala.concurrent.Future

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait OAuth2AssociationManager {

  def findByProviderInfo(providerUserId: String, provider: String): Future[Option[OAuth2Association]]

  def findForAccount(id: String): Future[Seq[OAuth2Association]]

  def findAll: Future[Seq[OAuth2Association]]

  def addAssociation(id: String, providerId: String, provider: String): Future[Option[OAuth2Association]]
}
