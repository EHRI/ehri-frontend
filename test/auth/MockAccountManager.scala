package auth

import java.util.UUID

import auth.sql.{SqlOpenIdAssociationManager, SqlOAuth2AssociationManager}
import models.Account
import utils.PageParams

import scala.concurrent.{ExecutionContext, Future}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class MockAccountManager() extends AccountManager {
  def oauth2: OAuth2AssociationManager = SqlOAuth2AssociationManager()
  def openid: OpenIdAssociationManager = SqlOpenIdAssociationManager()

  def setLoggedIn(account: Account): Future[Account] = ???

  def verify(account: Account, token: String): Future[Option[Account]] = ???

  def findById(id: String)(implicit executionContext: ExecutionContext): Future[Option[Account]] = ???

  def findAllById(ids: Seq[String])(implicit executionContext: ExecutionContext): Future[Seq[Account]] = ???

  def createResetToken(account: Account, uuid: UUID)(implicit executionContext: ExecutionContext): Future[Unit] = ???

  def findByResetToken(token: String, isSignUp: Boolean)(implicit executionContext: ExecutionContext): Future[Option[Account]] = ???

  def update(account: Account)(implicit executionContext: ExecutionContext): Future[Account] = ???

  def delete(account: Account)(implicit executionContext: ExecutionContext): Future[Account] = ???

  def expireTokens(account: Account)(implicit executionContext: ExecutionContext): Future[Unit] = ???

  def findAll(params: PageParams)(implicit executionContext: ExecutionContext): Future[Seq[Account]] = ???

  def createValidationToken(account: Account, uuid: UUID)(implicit executionContext: ExecutionContext): Future[Unit] = ???

  def findByEmail(email: String)(implicit executionContext: ExecutionContext): Future[Option[Account]] = ???

  def create(account: Account)(implicit executionContext: ExecutionContext): Future[Account] = ???

}
