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

  override def oAuth2: OAuth2AssociationManager = new OAuth2AssociationManager {
    protected implicit def executionContext: ExecutionContext = self.executionContext

    def addAssociation(id: String, providerId: String, provider: String): Future[Option[OAuth2Association]] =
      immediate {
        val oauth = OAuth2Association(id, providerId, provider, user= mocks.accountFixtures.get(id))
        mocks.oauth2AssociationFixtures += oauth
        Some(oauth)
      }

    def findAll: Future[Seq[OAuth2Association]] =
      immediate(mocks.oauth2AssociationFixtures.toSeq)

    def findByProviderInfo(providerUserId: String, provider: String): Future[Option[OAuth2Association]] =
      immediate(mocks.oauth2AssociationFixtures.find {
        assoc => assoc.providerId == providerUserId && assoc.provider == provider
      })

    def findForAccount(id: String): Future[Seq[OAuth2Association]] =
      immediate(mocks.oauth2AssociationFixtures.filter(_.id == id).toSeq)
  }

  override def openId: OpenIdAssociationManager = new OpenIdAssociationManager {
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

  override def get(id: String): Future[Account] =
    immediate(mocks.accountFixtures.getOrElse(id, throw new NoSuchElementException(id)))

  override def findById(id: String): Future[Option[Account]] =
    immediate(mocks.accountFixtures.get(id))

  override def findByEmail(email: String): Future[Option[Account]] =
    immediate(mocks.accountFixtures.values.find(_.email == email))

  override def findAllById(ids: Seq[String]): Future[Seq[Account]] =
    immediate(mocks.accountFixtures.filterKeys(id => ids.contains(id)).map(_._2).toSeq)

  override def authenticateById(id: String, pw: String, verifiedOnly: Boolean = false): Future[Option[Account]] = immediate {
    for {
      acc <- mocks.accountFixtures.values.find(_.id == id)
      hashed <- acc.password
      if hashed.check(pw) && (if(verifiedOnly) acc.verified else true)
    } yield acc
  }

  override def authenticateByEmail(email: String, pw: String, verifiedOnly: Boolean = false): Future[Option[Account]] = immediate {
    for {
      acc <- mocks.accountFixtures.values.find(_.email == email)
      hashed <- acc.password
      if hashed.check(pw) && (if(verifiedOnly) acc.verified else true)
    } yield acc
  }

  override def setLoggedIn(account: Account): Future[Account] =
    immediate(updateWith(account.copy(lastLogin = Some(DateTime.now()))))

  override def verify(account: Account, token: String): Future[Option[Account]] =
    immediate(Some(updateWith(account.copy(verified = true))))

  override def createToken(id: String, token: UUID, isSignUp: Boolean): Future[Unit] =
    immediate(mocks.tokenFixtures += ((token.toString, id, isSignUp)))

  override def findByToken(token: String, isSignUp: Boolean): Future[Option[Account]] = {
    mocks.tokenFixtures.find(t => t._1 == token && t._3 == isSignUp) match {
      case Some((t, p, s)) => findById(p)
      case _ => immediate(None)
    }
  }

  override def update(account: Account): Future[Account] =
    immediate(updateWith(account))

  override def delete(id: String): Future[Boolean] = immediate {
    mocks.accountFixtures -= id
    true
  }

  override def expireTokens(id: String): Future[Unit] = immediate {
    val indicesToDelete = for {
      (t, i) <- mocks.tokenFixtures.zipWithIndex if t._2 == id
    } yield i
    for (i <- (mocks.tokenFixtures.size -1) to 0 by -1) if (indicesToDelete contains i) mocks.tokenFixtures remove i
  }

  override def findAll(params: PageParams): Future[Seq[Account]] = immediate {
    val all = mocks.accountFixtures.values.toSeq.sortBy(_.id).drop(params.offset)
    if (params.hasLimit) all.drop(params.limit) else all
  }

  override def create(account: Account): Future[Account] =
    immediate(updateWith(account))
}
