package test

import helpers._
import models.{GroupF, Group, UserProfileF, UserProfile, Entity}
import controllers.routes
import play.api.test._
import play.api.test.Helpers._
import defines._
import rest.{ApiUser, EntityDAO}

/**
 * Spec to test various page views operate as expected.
 */
class EntityViewsSpec extends Neo4jRunnerSpec(classOf[EntityViewsSpec]) {
  import mocks.{privilegedUser,unprivilegedUser}

  implicit val apiUser: ApiUser = ApiUser(Some(privilegedUser.id))

  // Common headers/strings
  val multipleItemsHeader = "Displaying items"
  val oneItemHeader = "One item found"
  val noItemsHeader = "No items found"

  "HistoricalAgent views" should {

    "list should get some items" in new FakeApp {

      val list = route(fakeLoggedInHtmlRequest(unprivilegedUser, GET,
          controllers.authorities.routes.HistoricalAgents.list.url)).get
      status(list) must equalTo(OK)
      contentAsString(list) must contain(multipleItemsHeader)
      contentAsString(list) must contain("a1")
      contentAsString(list) must contain("a2")
    }

    "allow creating new items when logged in as privileged user" in new FakeApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("wiener-library"),
        "descriptions[0].languageCode" -> Seq("en"),
        "descriptions[0].typeOfEntity" -> Seq("corporateBody"),
        "descriptions[0].name" -> Seq("Wiener Library"),
        "descriptions[0].otherFormsOfName[0]" -> Seq("Wiener Library (Alt)"),
        "descriptions[0].parallelFormsOfName[0]" -> Seq("Wiener Library (Alt)"),
        "descriptions[0].descriptionArea.biographicalHistory" -> Seq("Some history"),
        "descriptions[0].descriptionArea.generalContext" -> Seq("Some content"),
        "publicationStatus" -> Seq("Published")
      )
      val cr = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.authorities.routes.AuthoritativeSets
          .createHistoricalAgent("auths").url).withHeaders(formPostHeaders.toSeq: _*), testData).get
      status(cr) must equalTo(SEE_OTHER)

      // FIXME: This route will change when a property ID mapping scheme is devised
      val show = route(fakeLoggedInHtmlRequest(privilegedUser, GET, redirectLocation(cr).get)).get
      status(show) must equalTo(OK)
      contentAsString(show) must contain("Some history")
      contentAsString(show) must contain("Some content")
    }

    "error if missing mandatory values" in new FakeApp {
      val testData: Map[String, Seq[String]] = Map(
      )
      val cr = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.authorities.routes.AuthoritativeSets
          .createHistoricalAgent("auths").url).withHeaders(formPostHeaders.toSeq: _*), testData).get
      status(cr) must equalTo(BAD_REQUEST)
    }

    "give a form error when creating items with an existing identifier" in new FakeApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("a1"),
        "descriptions[0].name" -> Seq("A test"),
        "descriptions[0].typeOfEntity" -> Seq("corporateBody"),
        "descriptions[0].languageCode" -> Seq("en")
      )
      val cr = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.authorities.routes.AuthoritativeSets
          .createHistoricalAgent("auths").url).withHeaders(formPostHeaders.toSeq: _*), testData).get
      status(cr) must equalTo(SEE_OTHER)
      val cr2 = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.authorities.routes.AuthoritativeSets
          .createHistoricalAgent("auths").url).withHeaders(formPostHeaders.toSeq: _*), testData).get
      status(cr2) must equalTo(BAD_REQUEST)
    }


    "link to other privileged actions when logged in" in new FakeApp {
      val show = route(fakeLoggedInHtmlRequest(privilegedUser, GET, controllers.authorities.routes.HistoricalAgents.get("a1").url)).get
      status(show) must equalTo(OK)
      contentAsString(show) must contain(controllers.authorities.routes.HistoricalAgents.update("a1").url)
      contentAsString(show) must contain(controllers.authorities.routes.HistoricalAgents.delete("a1").url)
      contentAsString(show) must contain(controllers.authorities.routes.HistoricalAgents.visibility("a1").url)
      contentAsString(show) must contain(controllers.authorities.routes.HistoricalAgents.search().url)
    }

    "allow updating items when logged in as privileged user" in new FakeApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("a1"),
        "descriptions[0].typeOfEntity" -> Seq("corporateBody"),
        "descriptions[0].languageCode" -> Seq("en"),
        "descriptions[0].name" -> Seq("An Authority"),
        "descriptions[0].otherFormsOfName[0]" -> Seq("An Authority (Alt)"),
        "descriptions[0].parallelFormsOfName[0]" -> Seq("An Authority 2 (Alt)"),
        "descriptions[0].descriptionArea.history" -> Seq("New History for a1"),
        "descriptions[0].descriptionArea.generalContext" -> Seq("New Content for a1"),
        "publicationStatus" -> Seq("Draft")
      )
      val cr = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.authorities.routes.HistoricalAgents.updatePost("a1").url).withHeaders(formPostHeaders.toSeq: _*), testData).get
      status(cr) must equalTo(SEE_OTHER)

      val show = route(fakeLoggedInHtmlRequest(privilegedUser, GET, redirectLocation(cr).get)).get
      status(show) must equalTo(OK)
      contentAsString(show) must contain("New Content for a1")
    }

    "disallow updating items when logged in as unprivileged user" in new FakeApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("a1")
      )
      val cr = route(fakeLoggedInHtmlRequest(unprivilegedUser, POST,
        controllers.authorities.routes.HistoricalAgents.updatePost("a1").url).withHeaders(formPostHeaders.toSeq: _*), testData).get
      status(cr) must equalTo(UNAUTHORIZED)
    }
  }

  "UserProfile views" should {

    import rest.PermissionDAO

    val subjectUser = UserProfile(UserProfileF(id = Some("reto"), identifier = "reto", name = "Reto"))
    val id = "reto"

    "reliably set permissions" in new FakeApp {
      val testData: Map[String, List[String]] = Map(
        ContentTypes.Repository.toString -> List(PermissionType.Create.toString),
        ContentTypes.DocumentaryUnit.toString -> List(PermissionType.Create.toString)
      )
      val cr = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.core.routes.UserProfiles.permissionsPost(subjectUser.id).url).withHeaders(formPostHeaders.toSeq: _*), testData).get
      status(cr) must equalTo(SEE_OTHER)

      // Now check we can read back the same permissions.
      val perms = await(PermissionDAO().get(subjectUser))
      perms.get(ContentTypes.Repository, PermissionType.Create) must beSome
      perms.get(ContentTypes.Repository, PermissionType.Create).get.inheritedFrom must beNone
      perms.get(ContentTypes.DocumentaryUnit, PermissionType.Create) must beSome
      perms.get(ContentTypes.DocumentaryUnit, PermissionType.Create).get.inheritedFrom must beNone
    }

    "link to other privileged actions when logged in" in new FakeApp {
      val show = route(fakeLoggedInHtmlRequest(privilegedUser, GET, controllers.core.routes.UserProfiles.get(id).url)).get
      status(show) must equalTo(OK)
      contentAsString(show) must contain(controllers.core.routes.UserProfiles.update(id).url)
      contentAsString(show) must contain(controllers.core.routes.UserProfiles.delete(id).url)
      contentAsString(show) must contain(controllers.core.routes.UserProfiles.permissions(id).url)
      contentAsString(show) must contain(controllers.core.routes.UserProfiles.grantList(id).url)
      contentAsString(show) must contain(controllers.core.routes.UserProfiles.search().url)
      contentAsString(show) must contain(controllers.core.routes.Groups.membership(EntityType.UserProfile.toString, id).url)
    }

    "allow adding users to groups" in new FakeApp {
      // Going to add user Reto to group Niod
      val add = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.core.routes.Groups.addMemberPost("niod", EntityType.UserProfile.toString, id).url)
          .withFormUrlEncodedBody()).get
      status(add) must equalTo(SEE_OTHER)

      val userFetch = await(EntityDAO(EntityType.UserProfile).get[UserProfile](id))
      userFetch.groups.map(_.id) must contain("niod")
    }

    "allow removing users from groups" in new FakeApp {
      // Going to add remove Reto from group KCL
      val rem = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.core.routes.Groups.removeMemberPost("kcl", EntityType.UserProfile.toString, id).url)
          .withFormUrlEncodedBody()).get
      status(rem) must equalTo(SEE_OTHER)

      val userFetch = await(EntityDAO(EntityType.UserProfile).get[UserProfile](id))
      userFetch.groups.map(_.id) must not contain("kcl")
    }
  }

  "Group views" should {

    val id = "kcl"

    "detail when logged in should link to other privileged actions" in new FakeApp {
      val show = route(fakeLoggedInHtmlRequest(privilegedUser, GET, controllers.core.routes.Groups.get(id).url)).get
      status(show) must equalTo(OK)
      contentAsString(show) must contain(controllers.core.routes.Groups.update(id).url)
      contentAsString(show) must contain(controllers.core.routes.Groups.delete(id).url)
      contentAsString(show) must contain(controllers.core.routes.Groups.permissions(id).url)
      contentAsString(show) must contain(controllers.core.routes.Groups.grantList(id).url)
      contentAsString(show) must contain(controllers.core.routes.Groups.membership(EntityType.Group.toString, id).url)
      contentAsString(show) must contain(controllers.core.routes.Groups.list().url)
    }

    "allow adding groups to groups" in new FakeApp {
      // Add KCL to Admin
      val add = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.core.routes.Groups.addMemberPost("admin", EntityType.Group.toString, id).url)
          .withFormUrlEncodedBody()).get
      status(add) must equalTo(SEE_OTHER)

      val groupFetch = await(EntityDAO(EntityType.Group).get[Group](id))
      groupFetch.groups.map(_.id) must contain("admin")
    }

    "allow removing groups from groups" in new FakeApp {
      // Remove NIOD from Admin
      val rem = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.core.routes.Groups.removeMemberPost("admin", EntityType.Group.toString, "niod").url)
          .withFormUrlEncodedBody()).get
      status(rem) must equalTo(SEE_OTHER)

      val groupFetch = await(EntityDAO(EntityType.Group).get[Group]("niod"))
      groupFetch.groups.map(_.id) must not contain("admin")
    }
  }
}
