package integration.portal

import helpers.Neo4jRunnerSpec
import controllers.portal.{ReverseSocial, ReversePortal}
import mocks.MockBufferedMailer
import backend.ApiUser

class SocialSpec extends Neo4jRunnerSpec(classOf[SocialSpec]) {
  import mocks.{privilegedUser, unprivilegedUser}

  private val socialRoutes: ReverseSocial = controllers.portal.routes.Social
  private val portalRoutes: ReversePortal = controllers.portal.routes.Portal

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

    "allow watching and unwatching items" in new FakeApp {
      val watch = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        socialRoutes.watchItemPost("c1").url), "").get
      status(watch) must equalTo(SEE_OTHER)

      // Watched items show up on the profile - maybe change this?
      val watching = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        socialRoutes.watching().url)).get
      // Check the following page contains a link to the user we just followed
      contentAsString(watching) must contain(
        portalRoutes.browseDocument("c1").url)

      // Unwatch
      val unwatch = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        socialRoutes.unwatchItemPost("c1").url), "").get
      status(unwatch) must equalTo(SEE_OTHER)

      val watching2 = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        socialRoutes.watching().url)).get
      // Check the profile contains no links to the item we just unwatched
      contentAsString(watching2) must not contain portalRoutes.browseDocument("c1").url

    }

    "show correct activity for watched items and followed users" in new FakeApp {

    }

    "allow messaging users" in new FakeApp {
      val numSentMails = MockBufferedMailer.mailBuffer.size
      val msgData = Map(
        "subject" -> Seq("Hello"),
        "message" -> Seq("World")
      )

      val postMsg = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        socialRoutes.sendMessagePost(unprivilegedUser.id).url), msgData).get
      status(postMsg) must equalTo(SEE_OTHER)
      MockBufferedMailer.mailBuffer.size must beEqualTo(numSentMails + 1)
      MockBufferedMailer.mailBuffer.last.text must contain("World")
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
