package backend.rest

import play.api.libs.concurrent.Execution.Implicits._
import backend.{EventHandler, Social, ApiUser}
import scala.concurrent.Future
import utils.ListParams
import models.UserProfile
import defines.EntityType
import models.json.RestReadable
import play.api.libs.json.Reads

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class SocialDAO(eventHandler: EventHandler) extends Social with RestDAO {

  def requestUrl = "http://%s:%d/%s/%s".format(host, port, mount, EntityType.UserProfile)

  def follow(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Unit] = {
    userCall(enc(requestUrl, userId, "follow", otherId)).post("").map { r =>
      checkError(r);
    }
  }
  def unfollow(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Unit] = {
    userCall(enc(requestUrl, userId, "unfollow", otherId)).post("").map { r =>
      checkError(r);
    }
  }
  def isFollowing(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Boolean] = {
    userCall(enc(requestUrl, userId, "isFollowing", otherId)).get().map { r =>
      checkErrorAndParse[Boolean](r);
    }
  }

  def isFollower(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Boolean] = {
    userCall(enc(requestUrl, userId, "isFollower", otherId)).get().map { r =>
      checkErrorAndParse[Boolean](r);
    }
  }

  def listFollowers(userId: String, params: ListParams)(implicit apiUser: ApiUser, rd: RestReadable[UserProfile]): Future[List[UserProfile]] = {
    userCall(enc(requestUrl, userId, "followers")).get().map { r =>
      checkErrorAndParse(r)(Reads.list(rd.restReads));
    }
  }

  def listFollowing(userId: String, params: ListParams)(implicit apiUser: ApiUser, rd: RestReadable[UserProfile]): Future[List[UserProfile]] = {
    userCall(enc(requestUrl, userId, "following")).get().map { r =>
      checkErrorAndParse(r)(Reads.list(rd.restReads));
    }
  }
}
