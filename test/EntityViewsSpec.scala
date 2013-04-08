package test

import org.neo4j.server.configuration.ThirdPartyJaxRsPackage
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeExample
import eu.ehri.extension.test.utils.ServerRunner
import eu.ehri.extension.AbstractAccessibleEntityResource
import helpers.TestMockLoginHelper
import play.api.http.HeaderNames
import models.UserProfile
import models.Entity
import models.base.Accessor
import controllers.ListParams
import models.{AnnotationType, AnnotationF}
import controllers.routes
import play.api.test._
import play.api.test.Helpers._
import defines._
import rest.{RestError, EntityDAO}
import play.api.GlobalSettings
import play.api.i18n.Messages


class EntityViewsSpec extends Specification with BeforeExample with TestMockLoginHelper {
  sequential // Needed to avoid concurrency issues with Neo4j databases.


  import mocks.UserFixtures.{privilegedUser,unprivilegedUser}

  val userProfile = UserProfile(Entity.fromString(privilegedUser.profile_id, EntityType.UserProfile)
    .withRelation(Accessor.BELONGS_REL, Entity.fromString("admin", EntityType.Group)))

  val testPort = 7575
  val config = Map("neo4j.server.port" -> testPort)

  object SimpleFakeGlobal extends GlobalSettings

  // Set up Neo4j server config
  val runner: ServerRunner = new ServerRunner(classOf[EntityViewsSpec].getName, testPort)
  runner.getConfigurator
    .getThirdpartyJaxRsClasses()
    .add(new ThirdPartyJaxRsPackage(classOf[AbstractAccessibleEntityResource[_]].getPackage.getName, "/ehri"))
  runner.start

  // Common headers/strings
  val multipleItemsHeader = "Displaying items"
  val oneItemHeader = "One item found"
  val noItemsHeader = "No items found"

  // Headers for post operations
  val postHeaders: Map[String, String] = Map(
    HeaderNames.CONTENT_TYPE -> "application/x-www-form-urlencoded"
  )


  def before = {
    runner.tearDown
    runner.setUp
  }


  "Repository views" should {

    "list should get some items" in {
      running(fakeLoginApplication(additionalConfiguration = config)) {
        val list = route(fakeLoggedInRequest(unprivilegedUser, GET, routes.Repositories.list().url)).get
        status(list) must equalTo(OK)
        contentAsString(list) must contain(multipleItemsHeader)
        contentAsString(list) must contain("r1")
        contentAsString(list) must contain("r2")
      }
    }

    "search should get some items" in {
      running(fakeLoginApplication(additionalConfiguration = config)) {
        val list = route(fakeLoggedInRequest(unprivilegedUser, GET, routes.Repositories.search().url)).get
        status(list) must equalTo(OK)
        contentAsString(list) must contain(multipleItemsHeader)
        contentAsString(list) must contain("r1")
        contentAsString(list) must contain("r2")
      }
    }

    "allow creating new items when logged in as privileged user" in {
      running(fakeLoginApplication(additionalConfiguration = config)) {
        val testData: Map[String, Seq[String]] = Map(
          "identifier" -> Seq("wiener-library"),
          "name" -> Seq("Wiener Library"),
          "descriptions[0].languageCode" -> Seq("en"),
          "descriptions[0].name" -> Seq("Wiener Library"),
          "descriptions[0].otherFormsOfName[0]" -> Seq("Wiener Library (Alt)"),
          "descriptions[0].parallelFormsOfName[0]" -> Seq("Wiener Library (Alt)"),
          "descriptions[0].descriptionArea.history" -> Seq("Some history"),
          "descriptions[0].descriptionArea.generalContext" -> Seq("Some content"),
          "descriptions[0].addressArea[0].name" -> Seq("An Address"),
          "publicationStatus" -> Seq("Published")
        )
        val cr = route(fakeLoggedInRequest(privilegedUser, POST,
          routes.Repositories.createPost.url).withHeaders(postHeaders.toSeq: _*), testData).get
        status(cr) must equalTo(SEE_OTHER)

        // FIXME: This route will change when a property ID mapping scheme is devised
        val show = route(fakeLoggedInRequest(privilegedUser, GET, redirectLocation(cr).get)).get
        status(show) must equalTo(OK)
        contentAsString(show) must contain("Some history")
        contentAsString(show) must contain("Some content")
        contentAsString(show) must contain("An Address")
      }
    }

    "error if missing mandatory values" in {
      running(fakeLoginApplication(additionalConfiguration = config)) {
        val testData: Map[String, Seq[String]] = Map(
        )
        val cr = route(fakeLoggedInRequest(privilegedUser, POST,
          routes.Repositories.createPost.url).withHeaders(postHeaders.toSeq: _*), testData).get
        status(cr) must equalTo(BAD_REQUEST)
      }
    }

    "give a form error when creating items with an existing identifier" in {
      running(fakeLoginApplication(additionalConfiguration = config)) {
        val testData: Map[String, Seq[String]] = Map(
          "identifier" -> Seq("r1")
        )
        val cr = route(fakeLoggedInRequest(privilegedUser, POST,
          routes.Repositories.createPost.url).withHeaders(postHeaders.toSeq: _*), testData).get
        status(cr) must equalTo(BAD_REQUEST)
      }
    }


    "link to other privileged actions when logged in" in {
      running(fakeLoginApplication(additionalConfiguration = config)) {
        val show = route(fakeLoggedInRequest(privilegedUser, GET, routes.Repositories.get("r1").url)).get
        status(show) must equalTo(OK)
        contentAsString(show) must contain(routes.Repositories.update("r1").url)
        contentAsString(show) must contain(routes.Repositories.delete("r1").url)
        contentAsString(show) must contain(routes.Repositories.createDoc("r1").url)
        contentAsString(show) must contain(routes.Repositories.visibility("r1").url)
        contentAsString(show) must contain(routes.Repositories.search().url)
      }
    }

    "allow updating items when logged in as privileged user" in {
      running(fakeLoginApplication(additionalConfiguration = config)) {
        val testData: Map[String, Seq[String]] = Map(
          "identifier" -> Seq("r1"),
          "name" -> Seq("Repository 1"),
          "descriptions[0].languageCode" -> Seq("en"),
          "descriptions[0].name" -> Seq("Repository 1"),
          "descriptions[0].otherFormsOfName[0]" -> Seq("Repository 1 (Alt)"),
          "descriptions[0].parallelFormsOfName[0]" -> Seq("Repository 1 (Alt)"),
          "descriptions[0].descriptionArea.history" -> Seq("New History for r1"),
          "descriptions[0].descriptionArea.generalContext" -> Seq("New Content for r1"),
          "publicationStatus" -> Seq("Draft")
        )
        val cr = route(fakeLoggedInRequest(privilegedUser, POST,
          routes.Repositories.updatePost("r1").url).withHeaders(postHeaders.toSeq: _*), testData).get
        status(cr) must equalTo(SEE_OTHER)

        val show = route(fakeLoggedInRequest(privilegedUser, GET, redirectLocation(cr).get)).get
        status(show) must equalTo(OK)
        contentAsString(show) must contain("New Content for r1")
      }
    }

    "disallow updating items when logged in as unprivileged user" in {
      running(fakeLoginApplication(additionalConfiguration = config)) {
        val testData: Map[String, Seq[String]] = Map(
          "identifier" -> Seq("r1"),
          "name" -> Seq("Repository 1"),
          "descriptions[0].languageCode" -> Seq("en"),
          "descriptions[0].name" -> Seq("Repository 1"),
          "descriptions[0].descriptionArea.generalContext" -> Seq("New Content for r1"),
          "publicationStatus" -> Seq("Draft")
        )
        val cr = route(fakeLoggedInRequest(unprivilegedUser, POST,
          routes.Repositories.updatePost("r1").url).withHeaders(postHeaders.toSeq: _*), testData).get
        status(cr) must equalTo(UNAUTHORIZED)

        // We can view the item when not logged in...
        val show = route(fakeLoggedInRequest(unprivilegedUser, GET, routes.Repositories.get("r1").url)).get
        status(show) must equalTo(OK)
        contentAsString(show) must not contain ("New Content for r1")
      }
    }
  }


  "HistoricalAgent views" should {

    "list should get some items" in {
      running(fakeLoginApplication(additionalConfiguration = config)) {
        val list = route(fakeLoggedInRequest(unprivilegedUser, GET, routes.HistoricalAgents.list().url)).get
        status(list) must equalTo(OK)
        contentAsString(list) must contain(multipleItemsHeader)
        contentAsString(list) must contain("a1")
        contentAsString(list) must contain("a2")
      }
    }

    "allow creating new items when logged in as privileged user" in {
      running(fakeLoginApplication(additionalConfiguration = config)) {
        val testData: Map[String, Seq[String]] = Map(
          "identifier" -> Seq("wiener-library"),
          "name" -> Seq("Wiener Library"),
          "descriptions[0].languageCode" -> Seq("en"),
          "descriptions[0].typeOfEntity" -> Seq("corporateBody"),
          "descriptions[0].name" -> Seq("Wiener Library"),
          "descriptions[0].otherFormsOfName[0]" -> Seq("Wiener Library (Alt)"),
          "descriptions[0].parallelFormsOfName[0]" -> Seq("Wiener Library (Alt)"),
          "descriptions[0].descriptionArea.biographicalHistory" -> Seq("Some history"),
          "descriptions[0].descriptionArea.generalContext" -> Seq("Some content"),
          "publicationStatus" -> Seq("Published")
        )
        val cr = route(fakeLoggedInRequest(privilegedUser, POST,
          routes.HistoricalAgents.createPost.url).withHeaders(postHeaders.toSeq: _*), testData).get
        status(cr) must equalTo(SEE_OTHER)

        // FIXME: This route will change when a property ID mapping scheme is devised
        val show = route(fakeLoggedInRequest(privilegedUser, GET, redirectLocation(cr).get)).get
        status(show) must equalTo(OK)
        contentAsString(show) must contain("Some history")
        contentAsString(show) must contain("Some content")
      }
    }

    "error if missing mandatory values" in {
      running(fakeLoginApplication(additionalConfiguration = config)) {
        val testData: Map[String, Seq[String]] = Map(
        )
        val cr = route(fakeLoggedInRequest(privilegedUser, POST,
          routes.HistoricalAgents.createPost.url).withHeaders(postHeaders.toSeq: _*), testData).get
        status(cr) must equalTo(BAD_REQUEST)
      }
    }

    "give a form error when creating items with an existing identifier" in {
      running(fakeLoginApplication(additionalConfiguration = config)) {
        val testData: Map[String, Seq[String]] = Map(
          "identifier" -> Seq("a1")
        )
        val cr = route(fakeLoggedInRequest(privilegedUser, POST,
          routes.HistoricalAgents.createPost.url).withHeaders(postHeaders.toSeq: _*), testData).get
        status(cr) must equalTo(BAD_REQUEST)
      }
    }


    "link to other privileged actions when logged in" in {
      running(fakeLoginApplication(additionalConfiguration = config)) {
        val show = route(fakeLoggedInRequest(privilegedUser, GET, routes.HistoricalAgents.get("a1").url)).get
        status(show) must equalTo(OK)
        contentAsString(show) must contain(routes.HistoricalAgents.update("a1").url)
        contentAsString(show) must contain(routes.HistoricalAgents.delete("a1").url)
        contentAsString(show) must contain(routes.HistoricalAgents.visibility("a1").url)
        contentAsString(show) must contain(routes.HistoricalAgents.search().url)
      }
    }

    "allow updating items when logged in as privileged user" in {
      running(fakeLoginApplication(additionalConfiguration = config)) {
        val testData: Map[String, Seq[String]] = Map(
          "identifier" -> Seq("a1"),
          "name" -> Seq("An Authority"),
          "descriptions[0].typeOfEntity" -> Seq("corporateBody"),
          "descriptions[0].languageCode" -> Seq("en"),
          "descriptions[0].name" -> Seq("An Authority"),
          "descriptions[0].otherFormsOfName[0]" -> Seq("An Authority (Alt)"),
          "descriptions[0].parallelFormsOfName[0]" -> Seq("An Authority 2 (Alt)"),
          "descriptions[0].descriptionArea.history" -> Seq("New History for a1"),
          "descriptions[0].descriptionArea.generalContext" -> Seq("New Content for a1"),
          "publicationStatus" -> Seq("Draft")
        )
        val cr = route(fakeLoggedInRequest(privilegedUser, POST,
          routes.HistoricalAgents.updatePost("a1").url).withHeaders(postHeaders.toSeq: _*), testData).get
        status(cr) must equalTo(SEE_OTHER)

        val show = route(fakeLoggedInRequest(privilegedUser, GET, redirectLocation(cr).get)).get
        status(show) must equalTo(OK)
        contentAsString(show) must contain("New Content for a1")
      }
    }

    "disallow updating items when logged in as unprivileged user" in {
      running(fakeLoginApplication(additionalConfiguration = config)) {
        val testData: Map[String, Seq[String]] = Map(
          "identifier" -> Seq("a1")
        )
        val cr = route(fakeLoggedInRequest(unprivilegedUser, POST,
          routes.HistoricalAgents.updatePost("a1").url).withHeaders(postHeaders.toSeq: _*), testData).get
        status(cr) must equalTo(UNAUTHORIZED)
      }
    }
  }

    "UserProfile views" should {

      import rest.PermissionDAO

      val subjectUser = UserProfile(Entity.fromString("reto", EntityType.UserProfile))
      val id = subjectUser.identifier

      "reliably set permissions" in {
        running(fakeLoginApplication(additionalConfiguration = config)) {
          val testData: Map[String, List[String]] = Map(
            ContentType.Repository.toString -> List(PermissionType.Create.toString),
            ContentType.DocumentaryUnit.toString -> List(PermissionType.Create.toString)
          )
          val cr = route(fakeLoggedInRequest(privilegedUser, POST,
            routes.UserProfiles.permissionsPost(subjectUser.identifier).url).withHeaders(postHeaders.toSeq: _*), testData).get
          status(cr) must equalTo(SEE_OTHER)

          // Now check we can read back the same permissions.
          val permCall = await(PermissionDAO[UserProfile](Some(userProfile)).get(subjectUser))
          permCall must beRight
          val perms = permCall.right.get
          perms.get(ContentType.Repository, PermissionType.Create) must beSome
          perms.get(ContentType.Repository, PermissionType.Create).get.inheritedFrom must beNone
          perms.get(ContentType.DocumentaryUnit, PermissionType.Create) must beSome
          perms.get(ContentType.DocumentaryUnit, PermissionType.Create).get.inheritedFrom must beNone
        }
      }

      "link to other privileged actions when logged in" in {
        running(fakeLoginApplication(additionalConfiguration = config)) {
          val show = route(fakeLoggedInRequest(privilegedUser, GET, routes.UserProfiles.get(id).url)).get
          status(show) must equalTo(OK)
          contentAsString(show) must contain(routes.UserProfiles.update(id).url)
          contentAsString(show) must contain(routes.UserProfiles.delete(id).url)
          contentAsString(show) must contain(routes.UserProfiles.permissions(id).url)
          contentAsString(show) must contain(routes.UserProfiles.grantList(id).url)
          contentAsString(show) must contain(routes.UserProfiles.list().url)
          contentAsString(show) must contain(routes.Groups.membership(EntityType.UserProfile.toString, id).url)
        }
      }

      "allow adding users to groups" in {
        running(fakeLoginApplication(additionalConfiguration = config)) {
          // Going to add user Reto to group Niod
          val add = route(fakeLoggedInRequest(privilegedUser, POST,
            routes.Groups.addMemberPost("niod", EntityType.UserProfile.toString, id).url)
              .withFormUrlEncodedBody()).get
          status(add) must equalTo(SEE_OTHER)

          val userFetch = await(EntityDAO(EntityType.UserProfile, Some(userProfile)).get(id))
          userFetch must beRight
          UserProfile(userFetch.right.get).groups.map(_.id) must contain("niod")
        }
      }

      "allow removing users from groups" in {
        running(fakeLoginApplication(additionalConfiguration = config)) {
          // Going to add remove Reto from group KCL
          val rem = route(fakeLoggedInRequest(privilegedUser, POST,
            routes.Groups.removeMemberPost("kcl", EntityType.UserProfile.toString, id).url)
              .withFormUrlEncodedBody()).get
          status(rem) must equalTo(SEE_OTHER)

          val userFetch = await(EntityDAO(EntityType.UserProfile, Some(userProfile)).get(id))
          userFetch must beRight
          UserProfile(userFetch.right.get).groups.map(_.id) must not contain("kcl")
        }
      }
    }

    "Group views" should {

      import models.Group

      val subjectUser = Group(Entity.fromString("kcl", EntityType.Group))
      val id = "kcl"

      "detail when logged in should link to other privileged actions" in {
        running(fakeLoginApplication(additionalConfiguration = config)) {
          val show = route(fakeLoggedInRequest(privilegedUser, GET, routes.Groups.get(id).url)).get
          status(show) must equalTo(OK)
          contentAsString(show) must contain(routes.Groups.update(id).url)
          contentAsString(show) must contain(routes.Groups.delete(id).url)
          contentAsString(show) must contain(routes.Groups.permissions(id).url)
          contentAsString(show) must contain(routes.Groups.grantList(id).url)
          contentAsString(show) must contain(routes.Groups.membership(EntityType.Group.toString, id).url)
          contentAsString(show) must contain(routes.Groups.list().url)
        }
      }

      "allow adding groups to groups" in {
        running(fakeLoginApplication(additionalConfiguration = config)) {
          // Add KCL to Admin
          val add = route(fakeLoggedInRequest(privilegedUser, POST,
            routes.Groups.addMemberPost("admin", EntityType.Group.toString, id).url)
              .withFormUrlEncodedBody()).get
          status(add) must equalTo(SEE_OTHER)

          val groupFetch = await(EntityDAO(EntityType.Group, Some(userProfile)).get(id))
          groupFetch must beRight
          Group(groupFetch.right.get).groups.map(_.id) must contain("admin")
        }
      }

      "allow removing groups from groups" in {
        running(fakeLoginApplication(additionalConfiguration = config)) {
          // Remove NIOD from Admin
          val rem = route(fakeLoggedInRequest(privilegedUser, POST,
            routes.Groups.removeMemberPost("admin", EntityType.Group.toString, "niod").url)
              .withFormUrlEncodedBody()).get
          status(rem) must equalTo(SEE_OTHER)

          val groupFetch = await(EntityDAO(EntityType.Group, Some(userProfile)).get("niod"))
          groupFetch must beRight
          Group(groupFetch.right.get).groups.map(_.id) must not contain("admin")
        }
      }
    }

  step {
    runner.stop
  }
}
