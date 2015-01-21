package auth

import java.util.UUID

import models.Account
import utils.PageParams

import scala.concurrent.{ExecutionContext, Future}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait AccountManager {

  def oAuth2: OAuth2AssociationManager
  def openId: OpenIdAssociationManager

  protected implicit def executionContext: ExecutionContext

  def authenticate(email: String, pw: String, verifiedOnly: Boolean = false): Future[Option[Account]]

  def setLoggedIn(account: Account): Future[Account]

  def verify(account: Account, token: String): Future[Option[Account]]

  def findById(id: String): Future[Option[Account]]

  def findByEmail(email: String): Future[Option[Account]]

  def findByResetToken(token: String, isSignUp: Boolean = false): Future[Option[Account]]

  def findAll(params: PageParams = PageParams.empty): Future[Seq[Account]]

  def findAllById(ids: Seq[String]): Future[Seq[Account]]

  def create(account: Account): Future[Account]

  def update(account: Account): Future[Account]

  def delete(id: String): Future[Boolean]

  def createResetToken(id: String, uuid: UUID): Future[Unit]

  def createValidationToken(id: String, uuid: UUID): Future[Unit]

  def expireTokens(id: String): Future[Unit]

  def setPassword(id: String, hashed: HashedPassword): Future[Unit]
}
