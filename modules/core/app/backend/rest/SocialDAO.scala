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

  def follow(userId: String)(implicit apiUser: ApiUser): Future[Unit] = {
    userCall(enc(requestUrl, "follow", userId)).post("").map { r =>
      checkError(r);
    }
  }
  def unfollow(userId: String)(implicit apiUser: ApiUser): Future[Unit] = {
    userCall(enc(requestUrl, "unfollow", userId)).post("").map { r =>
      checkError(r);
    }
  }
  def isFollowing(userId: String)(implicit apiUser: ApiUser): Future[Boolean] = {
    userCall(enc(requestUrl, "isFollowing", userId)).get().map { r =>
      checkErrorAndParse[Boolean](r);
    }
  }

  def isFollower(userId: String)(implicit apiUser: ApiUser): Future[Boolean] = {
    userCall(enc(requestUrl, "isFollower", userId)).get().map { r =>
      checkErrorAndParse[Boolean](r);
    }
  }

  def listFollowers(params: ListParams)(implicit apiUser: ApiUser, rd: RestReadable[UserProfile]): Future[List[UserProfile]] = {
    userCall(enc(requestUrl, "followers")).get().map { r =>
      checkErrorAndParse(r)(Reads.list(rd.restReads));
    }
  }

  def listFollowing(params: ListParams)(implicit apiUser: ApiUser, rd: RestReadable[UserProfile]): Future[List[UserProfile]] = {
    userCall(enc(requestUrl, "following")).get().map { r =>
      checkErrorAndParse(r)(Reads.list(rd.restReads));
    }
  }
}
