package integration

import scala.concurrent.ExecutionContext.Implicits.global

import helpers._
import models.UserProfile
import defines._
import models.MockAccount
import backend.ApiUser

/**
 * End-to-end test of the permissions system, implemented as one massive test.
 *
 * The purpose of this test is to:
 *
 *  - create a new group
 *  - assign permissions to this group to create docs and repos in a country (gb)
 *  - create a new user
 *  - add the user to the new group
 *  - create a repo in the country
 *  - create a doc in the repo
 *  - check that the user cannot write outside the country
 */
class CountryScopeIntegrationSpec extends Neo4jRunnerSpec(classOf[CountryScopeIntegrationSpec]) {
  import mocks.privilegedUser

  implicit val apiUser: ApiUser = ApiUser(Some(privilegedUser.id))

  private val repoRoutes = controllers.archdesc.routes.Repositories

  /**
   * Get the id from an URL where the ID is the last component...
   */
  private def idFromUrl(url: String) = url.substring(url.lastIndexOf("/") + 1)

  "The application" should {

    "support read/write on Repositories and Doc Units with country scope" in new FakeApp {
      // Target country
      val countryId = "gb"
      // Country we should NOT be able to write in...
      val otherCountryId = "nl"
      val otherRepoId = "r1"
      val otherDocId = "c4"


      // Create a new group, as our initial (admin) user
      val groupId = "uk-archivists"
      val groupData = Map(
        "identifier" -> Seq(groupId),
        "name" -> Seq("UK Archivists"),
        "description" -> Seq("Group for UK archivists")
      )
      val groupCreatePost = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.users.routes.Groups.create().url)
        .withHeaders(formPostHeaders.toSeq: _*), groupData).get
      status(groupCreatePost) must equalTo(SEE_OTHER)

      // Check we can read the group
      val groupRead = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        controllers.users.routes.Groups.get(groupId).url)).get
      status(groupRead) must equalTo(OK)

      // Grant scoped permissions for the group to create repos and docs in country gb
      val permissionsToGrant = List(
        PermissionType.Create, PermissionType.Update, PermissionType.Delete, PermissionType.Annotate
      )
      val permData: Map[String, List[String]] = Map(
        EntityType.Repository.toString -> permissionsToGrant.map(_.toString),
        EntityType.DocumentaryUnit.toString -> permissionsToGrant.map(_.toString)
      )
      val permSetPost = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
          controllers.archdesc.routes.Countries.setScopedPermissionsPost(countryId, EntityType.Group, groupId).url)
      .withHeaders(formPostHeaders.toSeq: _*), permData).get
      status(permSetPost) must equalTo(SEE_OTHER)

      // Okay, now create a new user and add them to the uk-archivists group. Do this
      // in one go using the groups parameter
      val userId = "test-user"
      val newUserData = Map(
        "identifier" -> Seq(userId),
        "name" -> Seq("Test User"),
        "email" -> Seq("test-user@example.com"),
        "password" -> Seq("changeme"),
        "confirm" -> Seq("changeme"),
        "group[]" -> Seq(groupId) // NB: Note brackets on param name!!!
      )
      val userCreatePost = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.users.routes.UserProfiles.createUserPost().url)
        .withHeaders(formPostHeaders.toSeq: _*), newUserData).get
      status(userCreatePost) must equalTo(SEE_OTHER)

      // Check we can read the user's page
      val userRead =  route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        controllers.users.routes.UserProfiles.get(userId).url)).get
      status(userRead) must equalTo(OK)

      // Fetch the user's profile to perform subsequent logins
      val profile = await(testBackend.get[UserProfile](userId))

      // TESTING MAGIC!!! We have to create an account for subsequent logins...
      // Then we add the account to the user fixtures (instead of adding it to the database,
      // which we don't have while testing.)
      val fakeAccount = MockAccount(userId, "test-user@example.com", verified = true, staff = true)
      mocks.userFixtures += fakeAccount.id -> fakeAccount

      // Check the user can read their profile as themselves...
      // Check we can read the user's page
      val userReadAsSelf =  route(fakeLoggedInHtmlRequest(fakeAccount, GET,
        controllers.users.routes.UserProfiles.get(userId).url)).get
      status(userReadAsSelf) must equalTo(OK)

      // Now we're going to create a repository as the new user
      val repoData = Map(
        "identifier" -> Seq("testrepo"),
        "descriptions[0].languageCode" -> Seq("en"),
        "descriptions[0].name" -> Seq("A Test Repository"),
        "descriptions[0].descriptionArea.history" -> Seq("A repository with a long history")
      )
      val repoCreatePost = route(fakeLoggedInHtmlRequest(fakeAccount, POST,
        controllers.archdesc.routes.Countries.createRepositoryPost(countryId).url)
        .withHeaders(formPostHeaders.toSeq: _*), repoData).get
      status(repoCreatePost) must equalTo(SEE_OTHER)

      // Test we can NOT create a repository in the other country...
      val otherRepoCreatePost = route(fakeLoggedInHtmlRequest(fakeAccount, POST,
        controllers.archdesc.routes.Countries.createRepositoryPost(otherCountryId).url)
          .withHeaders(formPostHeaders.toSeq: _*), repoData).get
      status(otherRepoCreatePost) must equalTo(UNAUTHORIZED)


      // Test we can read the new repository
      val repoId = idFromUrl(redirectLocation(repoCreatePost).get)
      val repoRead = route(fakeLoggedInHtmlRequest(fakeAccount, GET,
          repoRoutes.get(repoId).url)).get
      status(repoRead) must equalTo(OK)
      contentAsString(repoRead) must contain("A Test Repository")

      // Test we can create docs in this repository
      contentAsString(repoRead) must contain(repoRoutes.createDoc(repoId).url)

      // Test we can NOT create docs in repository r1, which is in country NL
      val otherRepoRead = route(fakeLoggedInHtmlRequest(fakeAccount, GET,
          repoRoutes.get(otherRepoId).url)).get
      status(otherRepoRead) must equalTo(OK)
      contentAsString(otherRepoRead) must not contain
          repoRoutes.createDoc(otherRepoId).url

      // Now create a documentary unit...
      val docData = Map(
        "identifier" -> Seq("testdoc"),
        "descriptions[0].languageCode" -> Seq("en"),
        "descriptions[0].identityArea.name" -> Seq("A new document"),
        "descriptions[0].contentArea.scopeAndContent" -> Seq("Lots...")
      )

      val createDocPost = route(fakeLoggedInHtmlRequest(fakeAccount, POST,
        repoRoutes.createDocPost(repoId).url).withHeaders(formPostHeaders.toSeq: _*), docData).get
      status(createDocPost) must equalTo(SEE_OTHER)

      // Test we can read the new repository
      val docId = idFromUrl(redirectLocation(createDocPost).get)
      val docRead = route(fakeLoggedInHtmlRequest(fakeAccount, GET,
          controllers.archdesc.routes.DocumentaryUnits.get(docId).url)).get
      status(docRead) must equalTo(OK)
      contentAsString(docRead) must contain("A new document")
      contentAsString(docRead) must contain(controllers.archdesc.routes.DocumentaryUnits.createDoc(docId).url)

      // Test we CAN'T create extra docs in an existing doc (c1)
      println("Checking cannot create in other doc...")
      val otherDocRead = route(fakeLoggedInHtmlRequest(fakeAccount, GET,
          controllers.archdesc.routes.DocumentaryUnits.get(otherDocId).url)).get
      status(otherDocRead) must equalTo(OK)
      contentAsString(otherDocRead) must not contain
          controllers.archdesc.routes.DocumentaryUnits.createDoc(otherDocId).url

    }
  }
}
