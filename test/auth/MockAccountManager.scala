package auth

import java.util.UUID

import models.{OpenIDAssociation, OAuth2Association, Account}
import org.joda.time.DateTime
import utils.PageParams

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.Future.{successful => immediate}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class MockAccountManager()(implicit app: play.api.Application) extends AccountManager {

  private val self = this

  override protected implicit def executionContext: ExecutionContext =
    play.api.libs.concurrent.Execution.Implicits.defaultContext

  private def updateWith(acc: Account): Account = {
    mocks.accountFixtures += acc.id -> acc
    acc
  }

  def oAuth2: OAuth2AssociationManager = new OAuth2AssociationManager {
    protected implicit def executionContext: ExecutionContext = self.executionContext

    def addAssociation(id: String, providerId: String, provider: String): Future[Option[OAuth2Association]] =
      immediate {
        val oauth = OAuth2Association(id, providerId, provider, user= mocks.accountFixtures.get(id))
        mocks.oauth2AssociationFixtures += oauth
        Some(oauth)
      }

    def findAll: Future[Seq[OAuth2Association]] = immediate(mocks.oauth2AssociationFixtures.toSeq)

    def findByProviderInfo(providerUserId: String, provider: String): Future[Option[OAuth2Association]] =
      immediate(mocks.oauth2AssociationFixtures.find {
        assoc => assoc.providerId == providerUserId && assoc.provider == provider
      })

    def findForAccount(id: String): Future[Seq[OAuth2Association]] =
      immediate(mocks.oauth2AssociationFixtures.filter(_.id == id).toSeq)
  }

  def openId: OpenIdAssociationManager = new OpenIdAssociationManager {
    protected implicit def executionContext: ExecutionContext = self.executionContext

    def addAssociation(id: String, assoc: String): Future[Option[OpenIDAssociation]] =
      immediate {
        val oid = OpenIDAssociation(id, url = assoc, user = mocks.accountFixtures.get(id))
        mocks.openIdAssociationFixtures += oid
        Some(oid)
      }

    def findByUrl(url: String): Future[Option[OpenIDAssociation]] =
      immediate(mocks.openIdAssociationFixtures.find(_.url == url))

    def findAll: Future[Seq[OpenIDAssociation]] = immediate(mocks.openIdAssociationFixtures.toSeq)
  }

  def authenticate(email: String, pw: String, verifiedOnly: Boolean = false): Future[Option[Account]] = {
    for (accountOpt <- findByEmail(email)) yield for {
      acc <- accountOpt
      hashed <- acc.password if hashed.check(pw) && (if(verifiedOnly) acc.verified else true)
    } yield acc
  }

  def setLoggedIn(account: Account): Future[Account] =
    immediate(updateWith(account.copy(lastLogin = Some(DateTime.now()))))

  def verify(account: Account, token: String): Future[Option[Account]] =
    immediate(Some(updateWith(account.copy(verified = true))))

  def findById(id: String): Future[Option[Account]] =
    immediate(mocks.accountFixtures.get(id))

  def findByEmail(email: String): Future[Option[Account]] =
    immediate(mocks.accountFixtures.values.find(_.email == email))

  def findAllById(ids: Seq[String]): Future[Seq[Account]] =
    immediate(mocks.accountFixtures.filterKeys(id => ids.contains(id)).map(_._2).toSeq)

  def createResetToken(id: String, token: UUID): Future[Unit] =
    immediate(mocks.tokenFixtures += ((token.toString, id, false)))

  def findByResetToken(token: String, isSignUp: Boolean): Future[Option[Account]] = {
    mocks.tokenFixtures.find(t => t._1 == token && t._3 == isSignUp) match {
      case Some((t, p, s)) => findById(p)
      case _ => immediate(None)
    }
  }

  def update(account: Account): Future[Account] =
    immediate(updateWith(account))

  def delete(id: String): Future[Boolean] = immediate {
    mocks.accountFixtures -= id
    true
  }

  def expireTokens(id: String): Future[Unit] = immediate {
    val indicesToDelete = for {
      (t, i) <- mocks.tokenFixtures.zipWithIndex if t._2 == id
    } yield i
    for (i <- (mocks.tokenFixtures.size -1) to 0 by -1) if (indicesToDelete contains i) mocks.tokenFixtures remove i
  }

  def findAll(params: PageParams): Future[Seq[Account]] =
    immediate(mocks.accountFixtures.values.toSeq)

  def createValidationToken(id: String, token: UUID): Future[Unit] = immediate {
    mocks.tokenFixtures += ((token.toString, id, true))
    Unit
  }

  def create(account: Account): Future[Account] =
    immediate(updateWith(account))

  def setPassword(id: String, hashed: HashedPassword): Future[Unit] =
    immediate(mocks.accountFixtures.get(id).map(acc => updateWith(acc.copy(password = Some(hashed)))))
}
