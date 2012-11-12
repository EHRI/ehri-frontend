package test

import org.junit.runner.RunWith
import org.neo4j.server.configuration.ThirdPartyJaxRsPackage
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.BeforeExample
import eu.ehri.plugin.test.utils.ServerRunner
import eu.ehri.extension.EhriNeo4jFramedResource
import helpers.TestLoginHelper
import play.api.test.FakeApplication
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.test.Helpers.contentAsString
import play.api.test.Helpers.route
import play.api.test.Helpers.running
import play.api.test.Helpers.status
import play.api.test.FakeHeaders
import play.api.mvc.AnyContentAsFormUrlEncoded
import play.api.http.HeaderNames
import models.UserProfileRepr
import models.Entity
import models.base.Accessor

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
@RunWith(classOf[JUnitRunner])
class EntityViewsSpec extends Specification with BeforeExample with TestLoginHelper {
  sequential

  import models.{ UserProfile, Group }
  import defines._
  //import play.api.http.HeaderNames

  val testPrivilegedUser = "mike"
  val testOrdinaryUser = "reto"
  val userProfile = UserProfileRepr(Entity.fromString("mike", EntityType.UserProfile)
		  .withRelation(Accessor.BELONGS_REL, Entity.fromString("admin", EntityType.Group)))

  val testPort = 7575
  val config = Map("neo4j.server.port" -> testPort)

  val runner: ServerRunner = new ServerRunner(classOf[ApplicationSpec].getName, testPort)
  runner.getConfigurator
    .getThirdpartyJaxRsClasses()
    .add(new ThirdPartyJaxRsPackage(classOf[EhriNeo4jFramedResource[_]].getPackage.getName, "/ehri"));
  runner.start

  def before = {
    runner.tearDown
    runner.setUp
  }

  def joinQueryString(qs: Map[String, Seq[String]]): String = {
    import java.net.URLEncoder
    qs.map {
      case (key, vals) => {
        vals.map(v => "%s=%s".format(key, URLEncoder.encode(v, "UTF-8")))
      }
    }.flatten.mkString("&")
  }

  "DocumentaryUnit views" should {

    import controllers.routes.DocumentaryUnits

    "list should get some (world-readable) items" in {
      running(FakeApplication(additionalConfiguration = config)) {
        val list = route(FakeRequest(GET, DocumentaryUnits.list(1, 20).url)).get
        status(list) must equalTo(OK)
        contentAsString(list) must contain("Displaying items")

        contentAsString(list) must not contain ("c1")
        contentAsString(list) must contain("c4")

      }
    }

    "list when logged in should get more items" in {
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
        val list = route(fakeLoggedInRequest(GET, DocumentaryUnits.list(1, 20).url)).get
        status(list) must equalTo(OK)
        contentAsString(list) must contain("Displaying items")
        contentAsString(list) must contain("c1")
        contentAsString(list) must contain("c2")
        contentAsString(list) must contain("c3")
        contentAsString(list) must contain("c4")
      }
    }

    "give access to c1 when logged in" in {
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
        val show = route(fakeLoggedInRequest(GET, DocumentaryUnits.get("c1").url)).get
        status(show) must equalTo(OK)
        contentAsString(show) must contain("c1")
      }
    }

    "deny access to c1 when logged in as an ordinary user" in {
      running(fakeLoginApplication(testOrdinaryUser, additionalConfiguration = config)) {
        val show = route(fakeLoggedInRequest(GET, DocumentaryUnits.get("c2").url)).get
        status(show) must equalTo(UNAUTHORIZED)
        contentAsString(show) must not contain ("c2")
      }
    }

    "allow access to c4 by default" in {
      running(FakeApplication(additionalConfiguration = config)) {
        val show = route(FakeRequest(GET, DocumentaryUnits.get("c4").url)).get
        status(show) must equalTo(OK)
        contentAsString(show) must contain("c4")
      }
    }

    "allow deleting c4 when logged in" in {
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
        val del = route(fakeLoggedInRequest(POST, DocumentaryUnits.deletePost("c4").url)).get
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
          "descriptions[0].scopeAndContent" -> Seq("Some content"),
          "publicationStatus" -> Seq("Published")
        )
        val headers: Map[String, String] = Map(HeaderNames.CONTENT_TYPE -> "application/x-www-form-urlencoded")
        val cr = route(fakeLoggedInRequest(POST,
          controllers.routes.Agents.docCreatePost("r1").url).withHeaders(headers.toSeq: _*), testData).get
        status(cr) must equalTo(SEE_OTHER)

        val show = route(fakeLoggedInRequest(GET, redirectLocation(cr).get)).get
        status(show) must equalTo(OK)
        contentAsString(show) must contain("Some content")
        contentAsString(show) must contain("Held by")
      }
    }

    "allow updating items when logged in as privileged user" in {
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
        val testData: Map[String, Seq[String]] = Map(
          "identifier" -> Seq("c1"),
          "name" -> Seq("Collection 1"),
          "descriptions[0].languageCode" -> Seq("en"),
          "descriptions[0].title" -> Seq("Collection 1"),
          "descriptions[0].scopeAndContent" -> Seq("New Content for c1"),
          "publicationStatus" -> Seq("Draft")
        )
        val headers: Map[String, String] = Map(HeaderNames.CONTENT_TYPE -> "application/x-www-form-urlencoded")
        val cr = route(fakeLoggedInRequest(POST,
          DocumentaryUnits.updatePost("c1").url).withHeaders(headers.toSeq: _*), testData).get
        status(cr) must equalTo(SEE_OTHER)

        val show = route(fakeLoggedInRequest(GET, DocumentaryUnits.get("c1").url)).get
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
          "descriptions[0].scopeAndContent" -> Seq("New Content for c4"),
          "publicationStatus" -> Seq("Draft")
        )
        val headers: Map[String, String] = Map(HeaderNames.CONTENT_TYPE -> "application/x-www-form-urlencoded")
        val cr = route(fakeLoggedInRequest(POST,
          DocumentaryUnits.updatePost("c4").url).withHeaders(headers.toSeq: _*), testData).get
        status(cr) must equalTo(UNAUTHORIZED)

        // We can view the item when not logged in...
        val show = route(fakeLoggedInRequest(GET, DocumentaryUnits.get("c4").url)).get
        status(show) must equalTo(OK)
        contentAsString(show) must not contain ("New Content for c4")
      }
    }

    "should redirect to login page when permission denied when not logged in" in {
      running(FakeApplication(additionalConfiguration = config)) {
        val show = route(FakeRequest(GET, DocumentaryUnits.get("c1").url)).get
        status(show) must equalTo(SEE_OTHER)
      }
    }
  }

  "Agent views" should {

    import controllers.routes.Agents

    "list should get some (world-readable) items" in {
      running(FakeApplication(additionalConfiguration = config)) {
        val list = route(FakeRequest(GET, Agents.list(1, 20).url)).get
        status(list) must equalTo(OK)
        contentAsString(list) must contain("Items")

        contentAsString(list) must contain ("r1")
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
          "descriptions[0].generalContext" -> Seq("Some content"),
          "publicationStatus" -> Seq("Published")
        )
        val headers: Map[String, String] = Map(HeaderNames.CONTENT_TYPE -> "application/x-www-form-urlencoded")
        val cr = route(fakeLoggedInRequest(POST,
          Agents.createPost.url).withHeaders(headers.toSeq: _*), testData).get
        status(cr) must equalTo(SEE_OTHER)

        val show = route(fakeLoggedInRequest(GET, Agents.get("wiener-library").url)).get
        status(show) must equalTo(OK)
        contentAsString(show) must contain("Some content")
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
          "descriptions[0].generalContext" -> Seq("New Content for r1"),
          "publicationStatus" -> Seq("Draft")
        )
        val headers: Map[String, String] = Map(HeaderNames.CONTENT_TYPE -> "application/x-www-form-urlencoded")
        val cr = route(fakeLoggedInRequest(POST,
          Agents.updatePost("r1").url).withHeaders(headers.toSeq: _*), testData).get
        status(cr) must equalTo(SEE_OTHER)

        val show = route(fakeLoggedInRequest(GET, Agents.get("r1").url)).get
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
          "descriptions[0].generalContext" -> Seq("New Content for r1"),
          "publicationStatus" -> Seq("Draft")
        )
        val headers: Map[String, String] = Map(HeaderNames.CONTENT_TYPE -> "application/x-www-form-urlencoded")
        val cr = route(fakeLoggedInRequest(POST,
          Agents.updatePost("r1").url).withHeaders(headers.toSeq: _*), testData).get
        status(cr) must equalTo(UNAUTHORIZED)

        // We can view the item when not logged in...
        val show = route(fakeLoggedInRequest(GET, Agents.get("r1").url)).get
        status(show) must equalTo(OK)
        contentAsString(show) must not contain ("New Content for r1")
      }
    }
  }
  
  "UserProfile views" should {

    import controllers.routes.UserProfiles
    import rest.PermissionDAO
    
    val subjectUser = UserProfileRepr(Entity.fromString("reto", EntityType.UserProfile))

    "reliably set permissions" in {
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
        val testData: Map[String, List[String]] = Map(
        	ContentType.Agent.toString -> List(PermissionType.Create.toString),
        	ContentType.DocumentaryUnit.toString -> List(PermissionType.Create.toString)
        )
        val headers: Map[String, String] = Map(HeaderNames.CONTENT_TYPE -> "application/x-www-form-urlencoded")
        val cr = route(fakeLoggedInRequest(POST,
          UserProfiles.permissionsPost(subjectUser.identifier).url).withHeaders(headers.toSeq: _*), testData).get
        println(contentAsString(cr))
        status(cr) must equalTo(SEE_OTHER)
        
        // Now check we can read back the same permissions.
        val permCall = await(PermissionDAO[UserProfileRepr](userProfile).get(subjectUser))
        permCall must beRight
        val perms = permCall.right.get
        perms.get(ContentType.Agent, PermissionType.Create) must beSome
        perms.get(ContentType.Agent, PermissionType.Create).get.inheritedFrom must beNone
        perms.get(ContentType.DocumentaryUnit, PermissionType.Create) must beSome
        perms.get(ContentType.DocumentaryUnit, PermissionType.Create).get.inheritedFrom must beNone        
      }
    }
  }

  step {
    runner.stop
  }
}