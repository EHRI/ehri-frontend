package auth.sql

import java.util.UUID

import auth.{OpenIdAssociationManager, OAuth2AssociationManager, AccountManager}
import models.Account
import utils.PageParams

import scala.concurrent.{Future, ExecutionContext}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class SqlAccountManager() extends AccountManager{

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
