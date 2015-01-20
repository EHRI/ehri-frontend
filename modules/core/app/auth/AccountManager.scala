package auth

import java.util.UUID

import models.Account
import utils.PageParams

import scala.concurrent.{ExecutionContext, Future}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait AccountManager {

  def oauth2: OAuth2AssociationManager
  def openid: OpenIdAssociationManager

  def authenticate(email: String, pw: String, verifiedOnly: Boolean = false)(implicit executionContext: ExecutionContext): Future[Option[Account]] = {
    for (accountOpt <- findByEmail(email)) yield for {
      acc <- accountOpt
      hashed <- acc.password if hashed.check(pw) && (if(verifiedOnly) acc.verified else true)
    } yield acc
  }

  def setLoggedIn(account: Account): Future[Account]

  def verify(account: Account, token: String): Future[Option[Account]]

  def findById(id: String)(implicit executionContext: ExecutionContext): Future[Option[Account]]

  def findByEmail(email: String)(implicit executionContext: ExecutionContext): Future[Option[Account]]

  def findByResetToken(token: String, isSignUp: Boolean = false)(implicit executionContext: ExecutionContext): Future[Option[Account]]

  def findAll(params: PageParams = PageParams.empty)(implicit executionContext: ExecutionContext): Future[Seq[Account]]

  def findAllById(ids: Seq[String])(implicit executionContext: ExecutionContext): Future[Seq[Account]]

  def create(account: Account)(implicit executionContext: ExecutionContext): Future[Account]

  def update(account: Account)(implicit executionContext: ExecutionContext): Future[Account]

  def delete(account: Account)(implicit executionContext: ExecutionContext): Future[Account]

  def createResetToken(account: Account, uuid: UUID)(implicit executionContext: ExecutionContext): Future[Unit]

  def createValidationToken(account: Account, uuid: UUID)(implicit executionContext: ExecutionContext): Future[Unit]

  def expireTokens(account: Account)(implicit executionContext: ExecutionContext): Future[Unit]
}
