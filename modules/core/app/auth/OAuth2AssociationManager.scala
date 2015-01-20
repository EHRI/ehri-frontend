package auth

import models.{OAuth2Association, Account}

import scala.concurrent.{ExecutionContext, Future}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait OAuth2AssociationManager {

  def findByProviderInfo(providerUserId: String, provider: String)(implicit executionContext: ExecutionContext): Future[Option[OAuth2Association]]

  def findForAccount(account: Account)(implicit executionContext: ExecutionContext): Future[Seq[OAuth2Association]]

  def findAll(implicit executionContext: ExecutionContext): Future[Seq[OAuth2Association]]

  def addAssociation(acc: Account, providerId: String, provider: String)(implicit executionContext: ExecutionContext): Future[OAuth2Association]
}
