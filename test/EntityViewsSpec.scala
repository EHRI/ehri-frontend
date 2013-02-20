package test

import org.neo4j.server.configuration.ThirdPartyJaxRsPackage
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeExample
import eu.ehri.extension.test.utils.ServerRunner
import eu.ehri.extension.AbstractAccessibleEntityResource
import helpers.TestLoginHelper
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


class EntityViewsSpec extends Specification with BeforeExample with TestLoginHelper {
  sequential // Needed to avoid concurrency issues with Neo4j databases.


  val testPrivilegedUser = "mike"
  val testOrdinaryUser = "reto"
  val userProfile = UserProfile(Entity.fromString("mike", EntityType.UserProfile)
    .withRelation(Accessor.BELONGS_REL, Entity.fromString("admin", EntityType.Group)))

  val testPort = 7575
  val config = Map("neo4j.server.port" -> testPort)

  // Set up Neo4j server config
  val runner: ServerRunner = new ServerRunner(classOf[ApplicationSpec].getName, testPort)
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

  "DocumentaryUnit views" should {

    "list should get some (world-readable) items" in {
      running(fakeLoginApplication(testOrdinaryUser, additionalConfiguration = config)) {
        val list = route(fakeLoggedInRequest(GET, routes.DocumentaryUnits.list.url)).get
        status(list) must equalTo(OK)
        contentAsString(list) must contain(oneItemHeader)

        contentAsString(list) must not contain ("c1")
        contentAsString(list) must contain("c4")

      }
    }

    "list when logged in should get more items" in {
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
        val list = route(fakeLoggedInRequest(GET, routes.DocumentaryUnits.list.url)).get
        status(list) must equalTo(OK)
        contentAsString(list) must contain(multipleItemsHeader)
        contentAsString(list) must contain("c1")
        contentAsString(list) must contain("c2")
        contentAsString(list) must contain("c3")
        contentAsString(list) must contain("c4")
      }
    }

    "list when logged with identifier filter in should get one" in {
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
        val params = s"${ListParams.PROPERTY_NAME}[0]=identifier&${ListParams.PROPERTY_VALUE}[0]=c3"
        val list = route(fakeLoggedInRequest(GET, routes.DocumentaryUnits.list.url + s"?$params")).get
        status(list) must equalTo(OK)
        contentAsString(list) must contain(oneItemHeader)
        contentAsString(list) must contain("c3")
      }
    }

    "link to other privileged actions when logged in" in {
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
        val show = route(fakeLoggedInRequest(GET, routes.DocumentaryUnits.get("c1").url)).get
        status(show) must equalTo(OK)
        contentAsString(show) must contain(routes.DocumentaryUnits.update("c1").url)
        contentAsString(show) must contain(routes.DocumentaryUnits.delete("c1").url)
        contentAsString(show) must contain(routes.DocumentaryUnits.createDoc("c1").url)
        contentAsString(show) must contain(routes.DocumentaryUnits.visibility("c1").url)
        contentAsString(show) must contain(routes.DocumentaryUnits.list().url)
      }
    }

    "link to holder" in {
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
        val show = route(fakeLoggedInRequest(GET, routes.DocumentaryUnits.get("c1").url)).get
        status(show) must equalTo(OK)

        contentAsString(show) must contain(routes.Repositories.get("r1").url)
      }
    }

    "link to holder when a child item" in {
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
        val show = route(fakeLoggedInRequest(GET, routes.DocumentaryUnits.get("c2").url)).get
        status(show) must equalTo(OK)

        contentAsString(show) must contain(routes.Repositories.get("r1").url)
      }
    }

    "give access to c1 when logged in" in {
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
        val show = route(fakeLoggedInRequest(GET, routes.DocumentaryUnits.get("c1").url)).get
        status(show) must equalTo(OK)
        contentAsString(show) must contain("c1")
      }
    }

    "deny access to c1 when logged in as an ordinary user" in {
      running(fakeLoginApplication(testOrdinaryUser, additionalConfiguration = config)) {
        val show = route(fakeLoggedInRequest(GET, routes.DocumentaryUnits.get("c2").url)).get
        status(show) must equalTo(UNAUTHORIZED)
        contentAsString(show) must not contain ("c2")
      }
    }

    "allow deleting c4 when logged in" in {
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
        val del = route(fakeLoggedInRequest(POST, routes.DocumentaryUnits.deletePost("c4").url)).get
        status(del) must equalTo(SEE_OTHER)
      }
    }

    "allow creating new items when logged in as privileged user" in {
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
        val testData: Map[String, Seq[String]] = Map(
          "identifier" -> Seq("hello-kitty"),
          "name" -> Seq("Hello Kitty"),
          "descriptions[0].languageCode" -> Seq("en"),
          "descriptions[0].title" -> Seq("Hello Kitty"),
          "descriptions[0].contentArea.scopeAndContent" -> Seq("Some content"),
          "publicationStatus" -> Seq("Published")
        )
        val cr = route(fakeLoggedInRequest(POST,
          controllers.routes.Repositories.createDocPost("r1").url).withHeaders(postHeaders.toSeq: _*), testData).get
        println(contentAsString(cr))
        status(cr) must equalTo(SEE_OTHER)

        val show = route(fakeLoggedInRequest(GET, redirectLocation(cr).get)).get
        status(show) must equalTo(OK)

        contentAsString(show) must contain("Some content")
        contentAsString(show) must contain("Held By")
        // After having created an item it should contain a 'history' pane
        // on the show page
        contentAsString(show) must contain(routes.DocumentaryUnits.history("r1-hello-kitty").url)
      }
    }

    "give a form error when creating items with the same id as existing ones" in {
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
        val testData: Map[String, Seq[String]] = Map(
          "identifier" -> Seq("c1"),
          "name" -> Seq("Dup Item"),
          "publicationStatus" -> Seq("Published")
        )
        // Since the item id is derived from the identifier field,
        // a form error should result from using the same identifier
        // twice within the given scope (in this case, r1)
        val call = fakeLoggedInRequest(POST,
            routes.Repositories.createDocPost("r1").url).withHeaders(postHeaders.toSeq: _*)
        val cr1 = route(call, testData).get
        status(cr1) must equalTo(SEE_OTHER) // okay the first time
        val cr2 = route(call, testData).get
        status(cr2) must equalTo(BAD_REQUEST)
        // NB: This error string comes from the server, so might
        // not match if changed there - single quotes surround the value
        contentAsString(cr2) must contain("exists and must be unique")
      }
    }

    "allow updating items when logged in as privileged user" in {
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
        val testData: Map[String, Seq[String]] = Map(
          "identifier" -> Seq("c1"),
          "name" -> Seq("Collection 1"),
          "descriptions[0].languageCode" -> Seq("en"),
          "descriptions[0].title" -> Seq("Collection 1"),
          "descriptions[0].contentArea.scopeAndContent" -> Seq("New Content for c1"),
          "descriptions[0].contextArea.acquistition" -> Seq("Acquisistion info"),
          "publicationStatus" -> Seq("Draft")
        )
        val cr = route(fakeLoggedInRequest(POST,
          routes.DocumentaryUnits.updatePost("c1").url).withHeaders(postHeaders.toSeq: _*), testData).get
        status(cr) must equalTo(SEE_OTHER)

        val show = route(fakeLoggedInRequest(GET, routes.DocumentaryUnits.get("c1").url)).get
        status(show) must equalTo(OK)
        contentAsString(show) must contain("New Content for c1")
      }
    }

    "disallow updating items when logged in as unprivileged user" in {
      running(fakeLoginApplication(testOrdinaryUser, additionalConfiguration = config)) {
        val testData: Map[String, Seq[String]] = Map(
          "identifier" -> Seq("c4"),
          "name" -> Seq("Collection 4"),
          "descriptions[0].languageCode" -> Seq("en"),
          "descriptions[0].title" -> Seq("Collection 4"),
          "descriptions[0].contentArea.scopeAndContent" -> Seq("New Content for c4"),
          "publicationStatus" -> Seq("Draft")
        )

        val cr = route(fakeLoggedInRequest(POST,
          routes.DocumentaryUnits.updatePost("c4").url).withHeaders(postHeaders.toSeq: _*), testData).get
        status(cr) must equalTo(UNAUTHORIZED)

        // We can view the item when not logged in...
        val show = route(fakeLoggedInRequest(GET, routes.DocumentaryUnits.get("c4").url)).get
        status(show) must equalTo(OK)
        contentAsString(show) must not contain ("New Content for c4")
      }
    }

    "should redirect to login page when permission denied when not logged in" in {
      running(FakeApplication(additionalConfiguration = config)) {
        val show = route(FakeRequest(GET, routes.DocumentaryUnits.get("c1").url)).get
        status(show) must equalTo(SEE_OTHER)
      }
    }

    "show history when logged in as privileged user" in {
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {

        val show = route(fakeLoggedInRequest(GET,
            routes.DocumentaryUnits.history("c1").url)).get
        status(show) must equalTo(OK)
      }
    }

    "allow granting permissions to create a doc within the scope of r2" in {
      import ContentType._
      val testRepo = "r2"
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("test"),
        "name" -> Seq("Test Item"),
        "descriptions[0].languageCode" -> Seq("en"),
        "descriptions[0].title" -> Seq("Test Item"),
        "descriptions[0].contentArea.scopeAndContent" -> Seq("A test"),
        "publicationStatus" -> Seq("Draft")
      )

      // Trying to create the item should fail initially.
      running(fakeLoginApplication(testOrdinaryUser, additionalConfiguration = config)) {
        // Check we cannot create an item...
        val cr = route(fakeLoggedInRequest(POST,
          routes.Repositories.createDocPost("r2").url).withHeaders(postHeaders.toSeq: _*), testData).get
        status(cr) must equalTo(UNAUTHORIZED)
      }

      // Grant permissions to create docs within the scope of r2
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
        val permTestData: Map[String,List[String]] = Map(
          DocumentaryUnit.toString -> List("create", "update", "delete")
        )
        val permReq = route(fakeLoggedInRequest(POST,
          routes.Repositories.setScopedPermissionsPost(testRepo, ContentType.UserProfile, testOrdinaryUser).url)
              .withHeaders(postHeaders.toSeq: _*), permTestData).get
        status(permReq) must equalTo(SEE_OTHER)
      }
      // Now try again and create the item... it should succeed.
      running(fakeLoginApplication(testOrdinaryUser, additionalConfiguration = config)) {
        // Check we cannot create an item...
        val cr = route(fakeLoggedInRequest(POST,
          routes.Repositories.createDocPost(testRepo).url).withHeaders(postHeaders.toSeq: _*), testData).get
        status(cr) must equalTo(SEE_OTHER)
        val getR = route(fakeLoggedInRequest(GET, redirectLocation(cr).get)).get
        status(getR) must equalTo(OK)
      }
    }

    "allow granting permissions on a specific item" in {
      import ContentType._
      val testItem = "c4"
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq(testItem),
        "name" -> Seq("Changed Name"),
        "descriptions[0].languageCode" -> Seq("en"),
        "descriptions[0].title" -> Seq("Changed Name"),
        "descriptions[0].contentArea.scopeAndContent" -> Seq("A test"),
        "publicationStatus" -> Seq("Draft")
      )

      // Trying to create the item should fail initially.
      running(fakeLoginApplication(testOrdinaryUser, additionalConfiguration = config)) {
        // Check we cannot create an item...
        val cr = route(fakeLoggedInRequest(POST,
          routes.DocumentaryUnits.updatePost(testItem).url).withHeaders(postHeaders.toSeq: _*), testData).get
        status(cr) must equalTo(UNAUTHORIZED)
      }

      // Grant permissions to update item c1
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
        val permTestData: Map[String,List[String]] = Map(
          DocumentaryUnit.toString -> List("update")
        )
        val permReq = route(fakeLoggedInRequest(POST,
          routes.DocumentaryUnits.setItemPermissionsPost(testItem, ContentType.UserProfile, testOrdinaryUser).url)
          .withHeaders(postHeaders.toSeq: _*), permTestData).get
        status(permReq) must equalTo(SEE_OTHER)
      }
      // Now try again to update the item, which should succeed
      running(fakeLoginApplication(testOrdinaryUser, additionalConfiguration = config)) {
        // Check we can update the item
        val cr = route(fakeLoggedInRequest(POST,
          routes.DocumentaryUnits.updatePost(testItem).url).withHeaders(postHeaders.toSeq: _*), testData).get
        status(cr) must equalTo(SEE_OTHER)
        val getR = route(fakeLoggedInRequest(GET, redirectLocation(cr).get)).get
        status(getR) must equalTo(OK)
      }
    }

    "allow commenting via annotations" in {
      val testItem = "c1"
      val body = "This is a neat annotation"
      val testData: Map[String,Seq[String]] = Map(
        AnnotationF.ANNOTATION_TYPE -> Seq(AnnotationType.Comment.toString),
        AnnotationF.BODY -> Seq(body)
      )
      // Now try again to update the item, which should succeed
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
        // Check we can update the item
        val cr = route(fakeLoggedInRequest(POST,
          routes.DocumentaryUnits.annotatePost(testItem).url).withHeaders(postHeaders.toSeq: _*), testData).get
        status(cr) must equalTo(SEE_OTHER)
        val getR = route(fakeLoggedInRequest(GET, redirectLocation(cr).get)).get
        status(getR) must equalTo(OK)
        contentAsString(getR) must contain(body)
      }
    }

    "allow linking to items via annotation" in {
      val testItem = "c1"
      val linkSrc = "cvocc1"
      val body = "This is a link"
      val testData: Map[String,Seq[String]] = Map(
        AnnotationF.ANNOTATION_TYPE -> Seq(AnnotationType.Link.toString),
        AnnotationF.BODY -> Seq(body)
      )
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
        val cr = route(fakeLoggedInRequest(POST,
          routes.DocumentaryUnits.linkAnnotatePost(testItem, EntityType.Concept.toString, linkSrc).url)
          .withHeaders(postHeaders.toSeq: _*), testData).get
        status(cr) must equalTo(SEE_OTHER)
        val getR = route(fakeLoggedInRequest(GET, redirectLocation(cr).get)).get
        status(getR) must equalTo(OK)
        contentAsString(getR) must contain(linkSrc)
        contentAsString(getR) must contain(body)
      }
    }

    "allow adding extra descriptions" in {
      val testItem = "c1"
      val testData: Map[String, Seq[String]] = Map(
        "languageCode" -> Seq("en"),
        "title" -> Seq("A Second Description"),
        "contentArea.scopeAndContent" -> Seq("This is a second description")
      )
      // Now try again to update the item, which should succeed
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
        // Check we can update the item
        val cr = route(fakeLoggedInRequest(POST,
          routes.DocumentaryUnits.createDescriptionPost(testItem).url)
          .withHeaders(postHeaders.toSeq: _*), testData).get
        status(cr) must equalTo(SEE_OTHER)
        val getR = route(fakeLoggedInRequest(GET, redirectLocation(cr).get)).get
        status(getR) must equalTo(OK)
        contentAsString(getR) must contain("This is a second description")
      }
    }

    "allow updating individual descriptions" in {
      val testItem = "c1"
      val testItemDesc = "cd1"
      val testData: Map[String, Seq[String]] = Map(
        "languageCode" -> Seq("en"),
        "id" -> Seq("cd1"),
        "title" -> Seq("An Updated Description"),
        "contentArea.scopeAndContent" -> Seq("This is an updated description")
      )
      // Now try again to update the item, which should succeed
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
        // Check we can update the item
        val cr = route(fakeLoggedInRequest(POST,
          routes.DocumentaryUnits.updateDescriptionPost(testItem, testItemDesc).url)
          .withHeaders(postHeaders.toSeq: _*), testData).get
        status(cr) must equalTo(SEE_OTHER)
        val getR = route(fakeLoggedInRequest(GET, redirectLocation(cr).get)).get
        status(getR) must equalTo(OK)
        contentAsString(getR) must contain("This is an updated description")
        contentAsString(getR) must not contain("Some description text for c1")
      }
    }

    "allow deleting individual descriptions" in {
      val testItem = "c1"
      val testItemDesc = "cd1-2"
      // Now try again to update the item, which should succeed
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
        // Check we can update the item
        val cr = route(fakeLoggedInRequest(POST,
          routes.DocumentaryUnits.deleteDescriptionPost(testItem, testItemDesc).url)
          .withHeaders(postHeaders.toSeq: _*)).get
        status(cr) must equalTo(SEE_OTHER)
        val getR = route(fakeLoggedInRequest(GET, redirectLocation(cr).get)).get
        status(getR) must equalTo(OK)
        contentAsString(getR) must not contain("Some alternate description text for c1")
      }
    }
  }



  "Repository views" should {

    "list should get some items" in {
      running(fakeLoginApplication(testOrdinaryUser, additionalConfiguration = config)) {
        val list = route(fakeLoggedInRequest(GET, routes.Repositories.list().url)).get
        status(list) must equalTo(OK)
        contentAsString(list) must contain(multipleItemsHeader)
        contentAsString(list) must contain("r1")
        contentAsString(list) must contain("r2")
      }
    }

    "allow creating new items when logged in as privileged user" in {
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
        val testData: Map[String, Seq[String]] = Map(
          "identifier" -> Seq("wiener-library"),
          "name" -> Seq("Wiener Library"),
          "descriptions[0].languageCode" -> Seq("en"),
          "descriptions[0].name" -> Seq("Wiener Library"),
          "descriptions[0].otherFormsOfName[0]" -> Seq("Wiener Library (Alt)"),
          "descriptions[0].parallelFormsOfName[0]" -> Seq("Wiener Library (Alt)"),
          "descriptions[0].descriptionArea.history" -> Seq("Some history"),
          "descriptions[0].descriptionArea.generalContext" -> Seq("Some content"),
          "descriptions[0].addressArea[0].addressName" -> Seq("An Address"),
          "publicationStatus" -> Seq("Published")
        )
        val cr = route(fakeLoggedInRequest(POST,
          routes.Repositories.createPost.url).withHeaders(postHeaders.toSeq: _*), testData).get
        status(cr) must equalTo(SEE_OTHER)

        // FIXME: This route will change when a property ID mapping scheme is devised
        val show = route(fakeLoggedInRequest(GET, redirectLocation(cr).get)).get
        status(show) must equalTo(OK)
        contentAsString(show) must contain("Some history")
        contentAsString(show) must contain("Some content")
        contentAsString(show) must contain("An Address")
      }
    }

    "error if missing mandatory values" in {
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
        val testData: Map[String, Seq[String]] = Map(
        )
        val cr = route(fakeLoggedInRequest(POST,
          routes.Repositories.createPost.url).withHeaders(postHeaders.toSeq: _*), testData).get
        status(cr) must equalTo(BAD_REQUEST)
      }
    }

    "give a form error when creating items with an existing identifier" in {
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
        val testData: Map[String, Seq[String]] = Map(
          "identifier" -> Seq("r1")
        )
        val cr = route(fakeLoggedInRequest(POST,
          routes.Repositories.createPost.url).withHeaders(postHeaders.toSeq: _*), testData).get
        status(cr) must equalTo(BAD_REQUEST)
      }
    }


    "link to other privileged actions when logged in" in {
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
        val show = route(fakeLoggedInRequest(GET, routes.Repositories.get("r1").url)).get
        status(show) must equalTo(OK)
        contentAsString(show) must contain(routes.Repositories.update("r1").url)
        contentAsString(show) must contain(routes.Repositories.delete("r1").url)
        contentAsString(show) must contain(routes.Repositories.createDoc("r1").url)
        contentAsString(show) must contain(routes.Repositories.visibility("r1").url)
        contentAsString(show) must contain(routes.Repositories.list().url)
      }
    }

    "allow updating items when logged in as privileged user" in {
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
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
        val cr = route(fakeLoggedInRequest(POST,
          routes.Repositories.updatePost("r1").url).withHeaders(postHeaders.toSeq: _*), testData).get
        status(cr) must equalTo(SEE_OTHER)

        val show = route(fakeLoggedInRequest(GET, redirectLocation(cr).get)).get
        status(show) must equalTo(OK)
        contentAsString(show) must contain("New Content for r1")
      }
    }

    "disallow updating items when logged in as unprivileged user" in {
      running(fakeLoginApplication(testOrdinaryUser, additionalConfiguration = config)) {
        val testData: Map[String, Seq[String]] = Map(
          "identifier" -> Seq("r1"),
          "name" -> Seq("Repository 1"),
          "descriptions[0].languageCode" -> Seq("en"),
          "descriptions[0].name" -> Seq("Repository 1"),
          "descriptions[0].descriptionArea.generalContext" -> Seq("New Content for r1"),
          "publicationStatus" -> Seq("Draft")
        )
        val cr = route(fakeLoggedInRequest(POST,
          routes.Repositories.updatePost("r1").url).withHeaders(postHeaders.toSeq: _*), testData).get
        status(cr) must equalTo(UNAUTHORIZED)

        // We can view the item when not logged in...
        val show = route(fakeLoggedInRequest(GET, routes.Repositories.get("r1").url)).get
        status(show) must equalTo(OK)
        contentAsString(show) must not contain ("New Content for r1")
      }
    }
  }

  "UserProfile views" should {

    import rest.PermissionDAO

    val subjectUser = UserProfile(Entity.fromString("reto", EntityType.UserProfile))
    val id = subjectUser.identifier

    "reliably set permissions" in {
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
        val testData: Map[String, List[String]] = Map(
          ContentType.Agent.toString -> List(PermissionType.Create.toString),
          ContentType.DocumentaryUnit.toString -> List(PermissionType.Create.toString)
        )
        val cr = route(fakeLoggedInRequest(POST,
          routes.UserProfiles.permissionsPost(subjectUser.identifier).url).withHeaders(postHeaders.toSeq: _*), testData).get
        status(cr) must equalTo(SEE_OTHER)

        // Now check we can read back the same permissions.
        val permCall = await(PermissionDAO[UserProfile](Some(userProfile)).get(subjectUser))
        permCall must beRight
        val perms = permCall.right.get
        perms.get(ContentType.Agent, PermissionType.Create) must beSome
        perms.get(ContentType.Agent, PermissionType.Create).get.inheritedFrom must beNone
        perms.get(ContentType.DocumentaryUnit, PermissionType.Create) must beSome
        perms.get(ContentType.DocumentaryUnit, PermissionType.Create).get.inheritedFrom must beNone
      }
    }

    "link to other privileged actions when logged in" in {
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
        val show = route(fakeLoggedInRequest(GET, routes.UserProfiles.get(id).url)).get
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
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
        // Going to add user Reto to group Niod
        val add = route(fakeLoggedInRequest(POST,
          routes.Groups.addMemberPost("niod", EntityType.UserProfile.toString, id).url)
            .withFormUrlEncodedBody()).get
        status(add) must equalTo(SEE_OTHER)

        val userFetch = await(EntityDAO(EntityType.UserProfile, Some(userProfile)).get(id))
        userFetch must beRight
        UserProfile(userFetch.right.get).groups.map(_.id) must contain("niod")
      }
    }

    "allow removing users from groups" in {
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
        // Going to add remove Reto from group KCL
        val rem = route(fakeLoggedInRequest(POST,
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
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
        val show = route(fakeLoggedInRequest(GET, routes.Groups.get(id).url)).get
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
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
        // Add KCL to Admin
        val add = route(fakeLoggedInRequest(POST,
          routes.Groups.addMemberPost("admin", EntityType.Group.toString, id).url)
            .withFormUrlEncodedBody()).get
        status(add) must equalTo(SEE_OTHER)

        val groupFetch = await(EntityDAO(EntityType.Group, Some(userProfile)).get(id))
        groupFetch must beRight
        Group(groupFetch.right.get).groups.map(_.id) must contain("admin")
      }
    }

    "allow removing groups from groups" in {
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
        // Remove NIOD from Admin
        val rem = route(fakeLoggedInRequest(POST,
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