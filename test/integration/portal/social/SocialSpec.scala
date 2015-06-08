package integration.portal.social

import helpers.IntegrationTestRunner
import controllers.portal.social.ReverseSocial
import play.api.test.FakeRequest

class SocialSpec extends IntegrationTestRunner {
  import mocks.{privilegedUser, unprivilegedUser}

  private val socialRoutes: ReverseSocial = controllers.portal.social.routes.Social

  override def getConfig = Map("recaptcha.skip" -> true)

  "Social views" should {
    "allow following and unfollowing users" in new ITestApp {
      val follow = FakeRequest(socialRoutes.followUserPost(unprivilegedUser.id))
        .withUser(privilegedUser).withCsrf.call()
      status(follow) must equalTo(SEE_OTHER)

      val following = FakeRequest(socialRoutes.followingForUser(privilegedUser.id))
        .withUser(privilegedUser).call()
      // Check the following page contains a link to the user we just followed
      contentAsString(following) must contain(
        socialRoutes.userProfile(unprivilegedUser.id).url)

      // Unfollow the sucker - he's boring...
      val unfollow = FakeRequest(socialRoutes.unfollowUserPost(unprivilegedUser.id))
        .withUser(privilegedUser).withCsrf.call()
      status(unfollow) must equalTo(SEE_OTHER)

      val following2 = FakeRequest(socialRoutes.followingForUser(privilegedUser.id))
        .withUser(privilegedUser).call()
      // Check the following page contains no links to the user we just unfollowed
      contentAsString(following2) must not contain socialRoutes.userProfile(unprivilegedUser.id).url
    }

    "allow messaging users" in new ITestApp {
      val numSentMails = mailBuffer.size
      val msgData = Map(
        "subject" -> Seq("Hello"),
        "message" -> Seq("World")
      )

      val postMsg = FakeRequest(socialRoutes.sendMessagePost(unprivilegedUser.id))
        .withUser(privilegedUser).withCsrf.callWith(msgData)
      status(postMsg) must equalTo(SEE_OTHER)
      mailBuffer.size must beEqualTo(numSentMails + 1)
      mailBuffer.last.bodyText.getOrElse("") must contain("World")
    }

    "allow messaging users with copy to self" in new ITestApp {
      val numSentMails = mailBuffer.size
      val msgData = Map(
        "subject" -> Seq("Hello"),
        "message" -> Seq("World"),
        "copySelf" -> Seq("true")
      )

      val postMsg = FakeRequest(socialRoutes.sendMessagePost(unprivilegedUser.id))
        .withUser(privilegedUser).withCsrf.callWith(msgData)
      status(postMsg) must equalTo(SEE_OTHER)
      mailBuffer.size must beEqualTo(numSentMails + 2)
      mailBuffer.last.bodyText.getOrElse("") must contain("World")
    }

    "disallow messaging users with messaging disabled" in new ITestApp {
      val user = unprivilegedUser
      mocks.accountFixtures += user.id -> user.copy(allowMessaging = false)

      val msgData = Map("subject" -> Seq("Hello"), "message" -> Seq("World"))

      val postMsg = FakeRequest(socialRoutes.sendMessage(user.id))
        .withUser(privilegedUser).call()
      status(postMsg) must equalTo(BAD_REQUEST)

      mocks.accountFixtures += user.id -> user.copy(allowMessaging = true)
      val postMsg2 = FakeRequest(socialRoutes.sendMessage(user.id))
        .withUser(privilegedUser).call()
      status(postMsg2) must equalTo(OK)
    }

    "disallow messaging users when blocked" in new ITestApp {
      val block = FakeRequest(socialRoutes.blockUserPost(privilegedUser.id))
        .withUser(unprivilegedUser).withCsrf.call()
      status(block) must equalTo(SEE_OTHER)

      val postMsg = FakeRequest(socialRoutes.sendMessage(unprivilegedUser.id))
        .withUser(privilegedUser).call()
      status(postMsg) must equalTo(BAD_REQUEST)
    }
  }
}
