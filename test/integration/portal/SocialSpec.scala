package integration.portal

import helpers.Neo4jRunnerSpec
import controllers.portal.{ReverseSocial, ReversePortal}

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
  }
}
