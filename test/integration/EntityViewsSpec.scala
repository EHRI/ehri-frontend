package integration

import helpers._
import models.{Group, UserProfileF, UserProfile}
import defines._
import backend.ApiUser
import play.api.test.FakeRequest

/**
 * Spec to test various page views operate as expected.
 */
class EntityViewsSpec extends IntegrationTestRunner {
  import mocks.{privilegedUser,unprivilegedUser}

  implicit val apiUser: ApiUser = ApiUser(Some(privilegedUser.id))

  // Common headers/strings
  val multipleItemsHeader = "Displaying items"
  val oneItemHeader = "One item found"
  val noItemsHeader = "No items found"

  "HistoricalAgent views" should {
    "list should get some items" in new ITestApp {

      val list = FakeRequest(controllers.authorities.routes.HistoricalAgents.list())
        .withUser(unprivilegedUser).call()
      status(list) must equalTo(OK)
      contentAsString(list) must contain(multipleItemsHeader)
      contentAsString(list) must contain("a1")
      contentAsString(list) must contain("a2")
    }

    "allow creating new items when logged in as privileged user" in new ITestApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("wiener-library"),
        "descriptions[0].languageCode" -> Seq("eng"),
        "descriptions[0].typeOfEntity" -> Seq("corporateBody"),
        "descriptions[0].name" -> Seq("Wiener Library"),
        "descriptions[0].otherFormsOfName[0]" -> Seq("Wiener Library (Alt)"),
        "descriptions[0].parallelFormsOfName[0]" -> Seq("Wiener Library (Alt)"),
        "descriptions[0].descriptionArea.biographicalHistory" -> Seq("Some history"),
        "descriptions[0].descriptionArea.generalContext" -> Seq("Some content"),
        "publicationStatus" -> Seq("Published")
      )
      val cr = FakeRequest(controllers.sets.routes.AuthoritativeSets.createHistoricalAgentPost("auths"))
        .withUser(privilegedUser).withCsrf.callWith(testData)
      status(cr) must equalTo(SEE_OTHER)

      val show = FakeRequest(GET, redirectLocation(cr).get).withUser(privilegedUser).call()
      status(show) must equalTo(OK)
      contentAsString(show) must contain("Some history")
      contentAsString(show) must contain("Some content")
    }

    "error if missing mandatory values" in new ITestApp {
      val testData: Map[String, Seq[String]] = Map(
      )
      val cr = FakeRequest(controllers.sets.routes.AuthoritativeSets.createHistoricalAgentPost("auths"))
        .withUser(privilegedUser).withCsrf.callWith(testData)
      status(cr) must equalTo(BAD_REQUEST)
    }

    "give a form error when creating items with an existing identifier" in new ITestApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("a1"),
        "descriptions[0].name" -> Seq("A test"),
        "descriptions[0].typeOfEntity" -> Seq("corporateBody"),
        "descriptions[0].languageCode" -> Seq("eng")
      )
      val cr = FakeRequest(controllers.sets.routes.AuthoritativeSets.createHistoricalAgentPost("auths"))
        .withUser(privilegedUser).withCsrf.callWith(testData)
      status(cr) must equalTo(SEE_OTHER)
      val cr2 = FakeRequest(controllers.sets.routes.AuthoritativeSets.createHistoricalAgentPost("auths"))
        .withUser(privilegedUser).withCsrf.callWith(testData)
      status(cr2) must equalTo(BAD_REQUEST)
    }


    "link to other privileged actions when logged in" in new ITestApp {
      val show = FakeRequest(controllers.authorities.routes.HistoricalAgents.get("a1"))
        .withUser(privilegedUser).call()
      status(show) must equalTo(OK)
      contentAsString(show) must contain(controllers.authorities.routes.HistoricalAgents.update("a1").url)
      contentAsString(show) must contain(controllers.authorities.routes.HistoricalAgents.delete("a1").url)
      contentAsString(show) must contain(controllers.authorities.routes.HistoricalAgents.visibility("a1").url)
      contentAsString(show) must contain(controllers.authorities.routes.HistoricalAgents.search().url)
    }

    "allow updating items when logged in as privileged user" in new ITestApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("a1"),
        "descriptions[0].typeOfEntity" -> Seq("corporateBody"),
        "descriptions[0].languageCode" -> Seq("eng"),
        "descriptions[0].name" -> Seq("An Authority"),
        "descriptions[0].otherFormsOfName[0]" -> Seq("An Authority (Alt)"),
        "descriptions[0].parallelFormsOfName[0]" -> Seq("An Authority 2 (Alt)"),
        "descriptions[0].descriptionArea.history" -> Seq("New History for a1"),
        "descriptions[0].descriptionArea.generalContext" -> Seq("New Content for a1"),
        "publicationStatus" -> Seq("Draft")
      )
      val cr = FakeRequest(controllers.authorities.routes.HistoricalAgents.updatePost("a1"))
        .withUser(privilegedUser).withCsrf.callWith(testData)
      status(cr) must equalTo(SEE_OTHER)

      val show = FakeRequest(GET, redirectLocation(cr).get).withUser(privilegedUser).call()
      status(show) must equalTo(OK)
      contentAsString(show) must contain("New Content for a1")
    }

    "disallow updating items when logged in as unprivileged user" in new ITestApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("a1")
      )
      val cr = FakeRequest(controllers.authorities.routes.HistoricalAgents.updatePost("a1"))
        .withUser(unprivilegedUser).withCsrf.callWith(testData)
      status(cr) must equalTo(FORBIDDEN)
    }

    "show correct default values in the form when creating new items" in new ITestApp(
      Map("historicalAgent.rulesAndConventions" -> "SOME RANDOM VALUE")) {
      val form = FakeRequest(controllers.sets.routes.AuthoritativeSets.createHistoricalAgent("auths"))
        .withUser(privilegedUser).call()
      status(form) must equalTo(OK)
      contentAsString(form) must contain("SOME RANDOM VALUE")
    }

    "contain links to external items" in new ITestApp {
      val show = FakeRequest(controllers.authorities.routes.HistoricalAgents.get("a1"))
        .withUser(privilegedUser).call()
      contentAsString(show) must contain("external-item-link")
      contentAsString(show) must contain(
        controllers.units.routes.DocumentaryUnits.get("c1").url)
    }
  }

  "UserProfile views" should {

    val id = "reto"
    val subjectUser = UserProfile(UserProfileF(id = Some(id), identifier = id, name = "Reto"))

    "reliably set permissions" in new ITestApp {
      val testData: Map[String, List[String]] = Map(
        ContentTypes.Repository.toString -> List(PermissionType.Create.toString),
        ContentTypes.DocumentaryUnit.toString -> List(PermissionType.Create.toString)
      )
      val cr = FakeRequest(controllers.users.routes.UserProfiles.permissionsPost(subjectUser.id))
        .withUser(privilegedUser).withCsrf.callWith(testData)
      status(cr) must equalTo(SEE_OTHER)

      // Now check we can read back the same permissions.
      val perms = await(testBackend.getGlobalPermissions(id))
      subjectUser.getPermission(perms, ContentTypes.Repository, PermissionType.Create) must beSome
      subjectUser.getPermission(perms, ContentTypes.Repository, PermissionType.Create).get.inheritedFrom must beNone
      subjectUser.getPermission(perms, ContentTypes.DocumentaryUnit, PermissionType.Create) must beSome
      subjectUser.getPermission(perms, ContentTypes.DocumentaryUnit, PermissionType.Create).get.inheritedFrom must beNone
    }

    "link to other privileged actions when logged in" in new ITestApp {
      val show = FakeRequest(controllers.users.routes.UserProfiles.get(id)).withUser(privilegedUser).call()
      status(show) must equalTo(OK)
      contentAsString(show) must contain(controllers.users.routes.UserProfiles.update(id).url)
      contentAsString(show) must contain(controllers.users.routes.UserProfiles.delete(id).url)
      contentAsString(show) must contain(controllers.users.routes.UserProfiles.permissions(id).url)
      contentAsString(show) must contain(controllers.users.routes.UserProfiles.grantList(id).url)
      contentAsString(show) must contain(controllers.users.routes.UserProfiles.search().url)
      contentAsString(show) must contain(controllers.users.routes.UserProfiles.membership(id).url)
    }

    "allow adding users to groups" in new ITestApp {
      // Going to add user Reto to group Niod
      val add = FakeRequest(controllers.users.routes.UserProfiles.addToGroup(id, "niod"))
          .withUser(privilegedUser).withCsrf.call()
      status(add) must equalTo(SEE_OTHER)

      val userFetch = await(testBackend.get[UserProfile](id))
      userFetch.groups.map(_.id) must contain("niod")
    }

    "allow updating account values" in new ITestApp {
      await(mockAccounts.findById(unprivilegedUser.id)) must beSome.which { before =>
        before.staff must beTrue
        before.active must beTrue
      }
      val data = Map("staff" -> Seq(false.toString), "active" -> Seq(false.toString))
      val update = FakeRequest(controllers.users.routes.UserProfiles.updatePost(unprivilegedUser.id))
        .withUser(privilegedUser).withCsrf.callWith(data)
      status(update) must equalTo(SEE_OTHER)
      await(mockAccounts.findById(unprivilegedUser.id)) must beSome.which { after =>
        after.staff must beFalse
        after.active must beFalse
      }
    }

    "not allow deletion unless confirmation is given" in new ITestApp {
      val del = FakeRequest(controllers.users.routes.UserProfiles.deletePost("reto"))
        .withUser(privilegedUser).withCsrf.call()
      status(del) must equalTo(BAD_REQUEST)
    }

    "allow deletion when confirmation is given" in new ITestApp {
      // Confirmation is the user's full name
      val data = Map("deleteCheck" -> Seq("Reto"))
      val del = FakeRequest(controllers.users.routes.UserProfiles.deletePost("reto"))
        .withUser(privilegedUser).withCsrf.callWith(data)
      status(del) must equalTo(SEE_OTHER)
    }

    "allow removing users from groups" in new ITestApp {
      // Going to add remove Reto from group KCL
      val rem = FakeRequest(controllers.users.routes.UserProfiles.removeFromGroup(id, "kcl"))
          .withUser(privilegedUser).withCsrf.call()
      status(rem) must equalTo(SEE_OTHER)

      val userFetch = await(testBackend.get[UserProfile](id))
      userFetch.groups.map(_.id) must not contain "kcl"
    }
  }

  "Group views" should {

    val id = "kcl"

    "detail when logged in should link to other privileged actions" in new ITestApp {
      val show = FakeRequest(controllers.groups.routes.Groups.get(id)).withUser(privilegedUser).call()
      status(show) must equalTo(OK)
      contentAsString(show) must contain(controllers.groups.routes.Groups.update(id).url)
      contentAsString(show) must contain(controllers.groups.routes.Groups.delete(id).url)
      contentAsString(show) must contain(controllers.groups.routes.Groups.permissions(id).url)
      contentAsString(show) must contain(controllers.groups.routes.Groups.grantList(id).url)
      contentAsString(show) must contain(controllers.groups.routes.Groups.membership(id).url)
      contentAsString(show) must contain(controllers.groups.routes.Groups.list().url)
    }

    "allow adding groups to groups" in new ITestApp {
      // Add KCL to Admin
      val add = FakeRequest(controllers.groups.routes.Groups.addToGroup(id, "admin"))
        .withUser(privilegedUser).withCsrf.call()
      status(add) must equalTo(SEE_OTHER)

      val groupFetch = await(testBackend.get[Group](id))
      groupFetch.groups.map(_.id) must contain("admin")
    }

    "allow removing groups from groups" in new ITestApp {
      // Remove NIOD from Admin
      val rem = FakeRequest(controllers.groups.routes.Groups.removeFromGroup("niod", "admin"))
          .withUser(privilegedUser).withCsrf.call()
      status(rem) must equalTo(SEE_OTHER)

      val groupFetch = await(testBackend.get[Group]("niod"))
      groupFetch.groups.map(_.id) must not contain "admin"
    }
  }

  "HistoricalAgent views" should {
    "list should get some items" in new ITestApp {

      val list = FakeRequest(controllers.authorities.routes.HistoricalAgents.list())
        .withUser(unprivilegedUser).call()
      status(list) must equalTo(OK)
      contentAsString(list) must contain(multipleItemsHeader)
      contentAsString(list) must contain("a1")
      contentAsString(list) must contain("a2")
    }

    "allow creating new items when logged in as privileged user" in new ITestApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("wiener-library"),
        "descriptions[0].languageCode" -> Seq("eng"),
        "descriptions[0].typeOfEntity" -> Seq("corporateBody"),
        "descriptions[0].name" -> Seq("Wiener Library"),
        "descriptions[0].otherFormsOfName[0]" -> Seq("Wiener Library (Alt)"),
        "descriptions[0].parallelFormsOfName[0]" -> Seq("Wiener Library (Alt)"),
        "descriptions[0].descriptionArea.biographicalHistory" -> Seq("Some history"),
        "descriptions[0].descriptionArea.generalContext" -> Seq("Some content"),
        "publicationStatus" -> Seq("Published")
      )
      val cr = FakeRequest(controllers.sets.routes.AuthoritativeSets.createHistoricalAgentPost("auths"))
        .withUser(privilegedUser).withCsrf.callWith(testData)
      status(cr) must equalTo(SEE_OTHER)

      val show = FakeRequest(GET, redirectLocation(cr).get)
        .withUser(privilegedUser).call()
      status(show) must equalTo(OK)
      contentAsString(show) must contain("Some history")
      contentAsString(show) must contain("Some content")
    }

    "error if missing mandatory values" in new ITestApp {
      val testData: Map[String, Seq[String]] = Map(
      )
      val cr = FakeRequest(controllers.sets.routes.AuthoritativeSets.createHistoricalAgentPost("auths"))
        .withUser(privilegedUser).withCsrf.callWith(testData)
      status(cr) must equalTo(BAD_REQUEST)
    }

    "give a form error when creating items with an existing identifier" in new ITestApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("a1"),
        "descriptions[0].name" -> Seq("A test"),
        "descriptions[0].typeOfEntity" -> Seq("corporateBody"),
        "descriptions[0].languageCode" -> Seq("eng")
      )
      val cr = FakeRequest(controllers.sets.routes.AuthoritativeSets.createHistoricalAgentPost("auths"))
        .withUser(privilegedUser).withCsrf.callWith(testData)
      status(cr) must equalTo(SEE_OTHER)
      val cr2 = FakeRequest(controllers.sets.routes.AuthoritativeSets.createHistoricalAgentPost("auths"))
        .withUser(privilegedUser).withCsrf.callWith(testData)
      status(cr2) must equalTo(BAD_REQUEST)
    }

    "link to other privileged actions when logged in" in new ITestApp {
      val show = FakeRequest(controllers.authorities.routes.HistoricalAgents.get("a1")).withUser(privilegedUser).call()
      status(show) must equalTo(OK)
      contentAsString(show) must contain(controllers.authorities.routes.HistoricalAgents.update("a1").url)
      contentAsString(show) must contain(controllers.authorities.routes.HistoricalAgents.delete("a1").url)
      contentAsString(show) must contain(controllers.authorities.routes.HistoricalAgents.visibility("a1").url)
      contentAsString(show) must contain(controllers.authorities.routes.HistoricalAgents.search().url)
    }

    "allow updating items when logged in as privileged user" in new ITestApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("a1"),
        "descriptions[0].typeOfEntity" -> Seq("corporateBody"),
        "descriptions[0].languageCode" -> Seq("eng"),
        "descriptions[0].name" -> Seq("An Authority"),
        "descriptions[0].otherFormsOfName[0]" -> Seq("An Authority (Alt)"),
        "descriptions[0].parallelFormsOfName[0]" -> Seq("An Authority 2 (Alt)"),
        "descriptions[0].descriptionArea.history" -> Seq("New History for a1"),
        "descriptions[0].descriptionArea.generalContext" -> Seq("New Content for a1"),
        "publicationStatus" -> Seq("Draft")
      )
      val cr = FakeRequest(controllers.authorities.routes.HistoricalAgents.updatePost("a1"))
        .withUser(privilegedUser).withCsrf.callWith(testData)
      status(cr) must equalTo(SEE_OTHER)

      val show = FakeRequest(GET, redirectLocation(cr).get).withUser(privilegedUser).call()
      status(show) must equalTo(OK)
      contentAsString(show) must contain("New Content for a1")
    }

    "disallow updating items when logged in as unprivileged user" in new ITestApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("a1")
      )
      val cr = FakeRequest(controllers.authorities.routes.HistoricalAgents.updatePost("a1"))
        .withUser(unprivilegedUser).withCsrf.callWith(testData)
      status(cr) must equalTo(FORBIDDEN)
    }

    "show correct default values in the form when creating new items" in new ITestApp(
      Map("historicalAgent.rulesAndConventions" -> "SOME RANDOM VALUE")) {
      val form = FakeRequest(controllers.sets.routes.AuthoritativeSets.createHistoricalAgent("auths"))
        .withUser(privilegedUser).call()
      status(form) must equalTo(OK)
      contentAsString(form) must contain("SOME RANDOM VALUE")
    }

    "contain links to external items" in new ITestApp {
      val show = FakeRequest(controllers.authorities.routes.HistoricalAgents.get("a1"))
        .withUser(privilegedUser).call()
      contentAsString(show) must contain("external-item-link")
      contentAsString(show) must contain(
        controllers.units.routes.DocumentaryUnits.get("c1").url)
    }
  }

  "Vocabulary views" should {
    "allow creating new items when logged in as privileged user" in new ITestApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("test-vocab"),
        "name" -> Seq("Test Vocab")
      )
      val cr = FakeRequest(controllers.vocabularies.routes.Vocabularies.createPost())
        .withUser(privilegedUser).withCsrf.callWith(testData)
      status(cr) must equalTo(SEE_OTHER)

      val show = FakeRequest(GET, redirectLocation(cr).get).withUser(privilegedUser).call()
      status(show) must equalTo(OK)
      contentAsString(show) must contain("Test Vocab")
    }

    "error if missing mandatory values" in new ITestApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("test-vocab")
      )
      val cr = FakeRequest(controllers.vocabularies.routes.Vocabularies.createPost())
        .withUser(privilegedUser).withCsrf.callWith(testData)
      status(cr) must equalTo(BAD_REQUEST)
    }

    "allow updating items when logged in as privileged user" in new ITestApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("cvoc1"),
        "name" -> Seq("Another Name")
      )
      val cr = FakeRequest(controllers.vocabularies.routes.Vocabularies.updatePost("cvoc1"))
        .withUser(privilegedUser).withCsrf.callWith(testData)
      status(cr) must equalTo(SEE_OTHER)

      val show = FakeRequest(GET, redirectLocation(cr).get)
        .withUser(privilegedUser).call()
      status(show) must equalTo(OK)
      contentAsString(show) must contain("Another Name")
    }
  }
}
