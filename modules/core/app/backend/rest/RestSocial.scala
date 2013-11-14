package backend.rest

import play.api.libs.concurrent.Execution.Implicits._
import backend.{EventHandler, Social, ApiUser}
import scala.concurrent.Future
import utils.ListParams
import models.UserProfile
import defines.EntityType
import models.json.RestReadable
import play.api.libs.json.Reads
import models.base.AnyModel

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait RestSocial extends Social with RestDAO {

  val eventHandler: EventHandler

  private def requestUrl = "http://%s:%d/%s/%s".format(host, port, mount, EntityType.UserProfile)

  def follow(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Unit] = {
    userCall(enc(requestUrl, userId, "follow", otherId)).post("").map { r =>
      checkError(r)
    }
  }
  def unfollow(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Unit] = {
    userCall(enc(requestUrl, userId, "follow", otherId)).delete().map { r =>
      checkError(r)
    }
  }
  def isFollowing(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Boolean] = {
    userCall(enc(requestUrl, userId, "isFollowing", otherId)).get().map { r =>
      checkErrorAndParse[Boolean](r)
    }
  }

  def isFollower(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Boolean] = {
    userCall(enc(requestUrl, userId, "isFollower", otherId)).get().map { r =>
      checkErrorAndParse[Boolean](r)
    }
  }

  def listFollowers(userId: String, params: ListParams)(implicit apiUser: ApiUser, rd: RestReadable[UserProfile]): Future[List[UserProfile]] = {
    userCall(enc(requestUrl, userId, "followers")).get().map { r =>
      checkErrorAndParse(r)(Reads.list(rd.restReads))
    }
  }

  def listFollowing(userId: String, params: ListParams)(implicit apiUser: ApiUser, rd: RestReadable[UserProfile]): Future[List[UserProfile]] = {
    userCall(enc(requestUrl, userId, "following")).get().map { r =>
      checkErrorAndParse(r)(Reads.list(rd.restReads))
    }
  }

  def listWatching(userId: String, params: ListParams)(implicit apiUser: ApiUser, rd: RestReadable[AnyModel]): Future[List[AnyModel]] = {
    userCall(enc(requestUrl, userId, "watching")).get().map { r =>
      checkErrorAndParse(r)(Reads.list(rd.restReads))
    }
  }

  def watch(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Unit] = {
    userCall(enc(requestUrl, userId, "watch", otherId)).post("").map { r =>
      checkError(r)
    }
  }

  def unwatch(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Unit] = {
    userCall(enc(requestUrl, userId, "watch", otherId)).delete().map { r =>
      checkError(r)
    }
  }

  def isWatching(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Boolean] = {
    userCall(enc(requestUrl, userId, "isWatching", otherId)).get().map { r =>
      checkErrorAndParse[Boolean](r)
    }
  }

}


case class SocialDAO(eventHandler: EventHandler) extends RestSocial