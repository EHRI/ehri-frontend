package integration.portal

import helpers.Neo4jRunnerSpec
import controllers.portal.{ReverseSocial, ReversePortal}
import mocks.MockBufferedMailer
import backend.ApiUser

class SocialSpec extends Neo4jRunnerSpec(classOf[SocialSpec]) {
  import mocks.{privilegedUser, unprivilegedUser}

  private val socialRoutes: ReverseSocial = controllers.portal.routes.Social

  override def getConfig = Map("recaptcha.skip" -> true)

  "Social views" should {
    "allow following and unfollowing users" in new FakeApp {
      val follow = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        socialRoutes.followUser(unprivilegedUser.id).url), "").get
      status(follow) must equalTo(SEE_OTHER)

      val following = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        socialRoutes.followingForUser(privilegedUser.id).url)).get
      // Check the following page contains a link to the user we just followed
      contentAsString(following) must contain(
        socialRoutes.browseUser(unprivilegedUser.id).url)

      // Unfollow the sucker - he's boring...
      val unfollow = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        socialRoutes.unfollowUser(unprivilegedUser.id).url), "").get
      status(unfollow) must equalTo(SEE_OTHER)

      val following2 = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        socialRoutes.followingForUser(privilegedUser.id).url)).get
      // Check the following page contains no links to the user we just unfollowed
      contentAsString(following2) must not contain socialRoutes.browseUser(unprivilegedUser.id).url
    }

    "allow messaging users" in new FakeApp {
      val numSentMails = mailBuffer.size
      val msgData = Map(
        "subject" -> Seq("Hello"),
        "message" -> Seq("World")
      )

      val postMsg = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        socialRoutes.sendMessagePost(unprivilegedUser.id).url), msgData).get
      status(postMsg) must equalTo(SEE_OTHER)
      mailBuffer.size must beEqualTo(numSentMails + 1)
      mailBuffer.last.text must contain("World")
    }

    "disallow messaging users with messaging disabled" in new FakeApp {
      val user = unprivilegedUser
      mocks.userFixtures += user.id -> user.copy(allowMessaging = false)

      val msgData = Map("subject" -> Seq("Hello"), "message" -> Seq("World"))

      val postMsg = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        socialRoutes.sendMessage(user.id).url)).get
      status(postMsg) must equalTo(BAD_REQUEST)

      mocks.userFixtures += user.id -> user.copy(allowMessaging = true)
      val postMsg2 = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        socialRoutes.sendMessage(user.id).url)).get
      status(postMsg2) must equalTo(OK)
    }

    "disallow messaging users when blocked" in new FakeApp {
      val block = route(fakeLoggedInHtmlRequest(unprivilegedUser, POST,
        socialRoutes.blockUser(privilegedUser.id).url)).get
      status(block) must equalTo(SEE_OTHER)

      val postMsg = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        socialRoutes.sendMessage(unprivilegedUser.id).url)).get
      status(postMsg) must equalTo(BAD_REQUEST)
    }
  }
}
