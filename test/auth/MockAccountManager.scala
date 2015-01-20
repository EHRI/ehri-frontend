package auth

import java.util.UUID

import auth.sql.{SqlOpenIdAssociationManager, SqlOAuth2AssociationManager}
import models.Account
import org.joda.time.DateTime
import utils.PageParams

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.Future.{successful => immediate}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class MockAccountManager() extends AccountManager {
  
  def oauth2: OAuth2AssociationManager = SqlOAuth2AssociationManager()
  def openid: OpenIdAssociationManager = SqlOpenIdAssociationManager()

  private def updateWith(acc: Account): Account = {
    mocks.userFixtures += acc.id -> acc
    acc
  }

  def setLoggedIn(account: Account): Future[Account] =
    immediate(updateWith(account.copy(lastLogin = Some(DateTime.now()))))

  def verify(account: Account, token: String): Future[Option[Account]] =
    immediate(Some(updateWith(account.copy(verified = true))))

  def findById(id: String)(implicit executionContext: ExecutionContext): Future[Option[Account]] =
    immediate(mocks.userFixtures.get(id))

  def findByEmail(email: String)(implicit executionContext: ExecutionContext): Future[Option[Account]] =
    immediate(mocks.userFixtures.values.find(_.email == email))

  def findAllById(ids: Seq[String])(implicit executionContext: ExecutionContext): Future[Seq[Account]] =
    immediate(mocks.userFixtures.filterKeys(id => ids.contains(id)).map(_._2).toSeq)

  def createResetToken(account: Account, token: UUID)(implicit executionContext: ExecutionContext): Future[Unit] =
    immediate(mocks.tokens += ((token.toString, account.id, false)))

  def findByResetToken(token: String, isSignUp: Boolean)(implicit executionContext: ExecutionContext): Future[Option[Account]] = {
    mocks.tokens.find(t => t._1 == token && t._3 == isSignUp) match {
      case Some((t, p, s)) => findById(p)
      case _ => immediate(None)
    }
  }

  def update(account: Account)(implicit executionContext: ExecutionContext): Future[Account] =
    immediate(updateWith(account))

  def delete(account: Account)(implicit executionContext: ExecutionContext): Future[Account] = immediate {
    mocks.userFixtures -= account.id
    account
  }

  def expireTokens(account: Account)(implicit executionContext: ExecutionContext): Future[Unit] = immediate {
    val indicesToDelete = for {
      (t, i) <- mocks.tokens.zipWithIndex if t._2 == account.id
    } yield i
    for (i <- (mocks.tokens.size -1) to 0 by -1) if (indicesToDelete contains i) mocks.tokens remove i
  }

  def findAll(params: PageParams)(implicit executionContext: ExecutionContext): Future[Seq[Account]] =
    immediate(mocks.userFixtures.values.toSeq)

  def createValidationToken(account: Account, token: UUID)(implicit executionContext: ExecutionContext): Future[Unit] = immediate {
    mocks.tokens += ((token.toString, account.id, true))
    Unit
  }

  def create(account: Account)(implicit executionContext: ExecutionContext): Future[Account] =
    immediate(updateWith(account))
}
