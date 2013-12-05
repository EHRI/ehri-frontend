package test

import helpers.{formPostHeaders,Neo4jRunnerSpec}
import models._
import play.api.i18n.Messages
import play.api.test.FakeRequest
import play.api.http.{MimeTypes, HeaderNames}
import backend.rest.PermissionDenied


class PortalSpec extends Neo4jRunnerSpec(classOf[PortalSpec]) {
  import mocks.{privilegedUser, unprivilegedUser}

  val userProfile = UserProfile(
    model = UserProfileF(id = Some(privilegedUser.id), identifier = "test", name="test user"),
    groups = List(Group(GroupF(id = Some("admin"), identifier = "admin", name="Administrators")))
  )

  "Portal views" should {
    "allow following and unfollowing users" in new FakeApp {

    }

    "allow watching and unwatching items" in new FakeApp {

    }

    "show correct activity for watched items and followed users" in new FakeApp {

    }

    "allow annotating items with correct visibility" in new FakeApp {

    }

    "allow updating annotations" in new FakeApp {

    }

    "allow changing annotation visibility" in new FakeApp {

    }

    "allow deleting annotations" in new FakeApp {

    }

    "allow linking items" in new FakeApp {

    }

    "allow deleting links" in new FakeApp {

    }

    "allow changing link visibility" in new FakeApp {

    }
  }
}
