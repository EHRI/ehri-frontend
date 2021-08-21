package integration.admin

import helpers._
import models.{Account, EntityType, PermissionType, UserProfile}
import play.api.test.FakeRequest
import services.data.DataUser

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
class CountryScopeIntegrationSpec extends IntegrationTestRunner {
  import mockdata.privilegedUser

  implicit val apiUser: DataUser = DataUser(Some(privilegedUser.id))

  private val docRoutes = controllers.units.routes.DocumentaryUnits
  private val repoRoutes = controllers.institutions.routes.Repositories
  private val groupRoutes = controllers.groups.routes.Groups
  private val userRoutes = controllers.users.routes.UserProfiles
  private val countryRoutes = controllers.countries.routes.Countries

  /**
   * Get the id from an URL where the ID is the last component...
   */
  private def idFromUrl(url: String) = url.substring(url.lastIndexOf("/") + 1)

  "The application" should {

    "support read/write on Repositories and Doc Units with country scope" in new ITestApp {
      // Target country
      val countryId = "gb"
      // Country we should NOT be able to write in...
      val otherCountryId = "nl"
      val otherRepoId = "r1"
      val otherDocId = "c4"


      // Create a new group, as our initial (admin) user
      val groupId = "ukarchivists"
      val groupData = Map(
        "identifier" -> Seq(groupId),
        "name" -> Seq("UK Archivists"),
        "description" -> Seq("Group for UK archivists")
      )
      val groupCreatePost = FakeRequest(groupRoutes.createPost())
        .withUser(privilegedUser).withCsrf.callWith(groupData)
      status(groupCreatePost) must equalTo(SEE_OTHER)

      // Check we can read the group
      val groupRead = FakeRequest(groupRoutes.get(groupId))
        .withUser(privilegedUser).call()
      status(groupRead) must equalTo(OK)

      // Grant scoped permissions for the group to create repos and docs in country gb
      val permissionsToGrant = List(
        PermissionType.Create, PermissionType.Update, PermissionType.Delete, PermissionType.Annotate
      )
      val permData: Map[String, List[String]] = Map(
        EntityType.Repository.toString -> permissionsToGrant.map(_.toString),
        EntityType.DocumentaryUnit.toString -> permissionsToGrant.map(_.toString)
      )
      val permSetPost = FakeRequest(countryRoutes
            .setScopedPermissionsPost(countryId, EntityType.Group, groupId))
        .withUser(privilegedUser).withCsrf.callWith(permData)
      status(permSetPost) must equalTo(SEE_OTHER)

      // Okay, now create a new user and add them to the ukarchivists group. Do this
      // in one go using the groups parameter
      val userId = "testuser"
      val newUserData = Map(
        "identifier" -> Seq(userId),
        "name" -> Seq("Test User"),
        "email" -> Seq("testuser@example.com"),
        "password" -> Seq("changeme"),
        "confirm" -> Seq("changeme"),
        "group[]" -> Seq(groupId) // NB: Note brackets on param name!!!
      )
      val userCreatePost = FakeRequest(userRoutes.createUserPost())
        .withUser(privilegedUser).withCsrf.callWith(newUserData)
      status(userCreatePost) must equalTo(SEE_OTHER)

      // Check we can read the user's page
      val userRead =  FakeRequest(userRoutes.get(userId))
        .withUser(privilegedUser).call()
      status(userRead) must equalTo(OK)

      // Fetch the user's profile to perform subsequent logins
      val profile = await(dataApi.get[UserProfile](userId))

      // TESTING MAGIC!!! We have to create an account for subsequent logins...
      // Then we add the account to the user fixtures (instead of adding it to the database,
      // which we don't have while testing.)
      val fakeAccount = Account(userId, "testuser@example.com", verified = true, staff = true)
      mockdata.accountFixtures += fakeAccount.id -> fakeAccount

      // Check the user can read their profile as themselves...
      // Check we can read the user's page
      val userReadAsSelf =  FakeRequest(userRoutes.get(userId))
        .withUser(fakeAccount).call()
      status(userReadAsSelf) must equalTo(OK)

      // Now we're going to create a repository as the new user
      val repoData = Map(
        "identifier" -> Seq("testrepo"),
        "descriptions[0].languageCode" -> Seq("eng"),
        "descriptions[0].name" -> Seq("A Test Repository"),
        "descriptions[0].descriptionArea.history" -> Seq("A repository with a long history")
      )
      val repoCreatePost = FakeRequest(countryRoutes.createRepositoryPost(countryId))
        .withUser(fakeAccount).withCsrf.callWith(repoData)
      status(repoCreatePost) must equalTo(SEE_OTHER)

      // Test we can NOT create a repository in the other country...
      val otherRepoCreatePost = FakeRequest(countryRoutes.createRepositoryPost(otherCountryId))
        .withUser(fakeAccount).withCsrf.callWith(repoData)
      status(otherRepoCreatePost) must equalTo(FORBIDDEN)


      // Test we can read the new repository
      val repoId = idFromUrl(redirectLocation(repoCreatePost).get)
      val repoRead = FakeRequest(repoRoutes.get(repoId)).withUser(fakeAccount).call()
      status(repoRead) must equalTo(OK)
      contentAsString(repoRead) must contain("A Test Repository")

      // Test we can create docs in this repository
      contentAsString(repoRead) must contain(repoRoutes.createDoc(repoId).url)

      // Test we can NOT create docs in repository r1, which is in country NL
      val otherRepoRead = FakeRequest(repoRoutes.get(otherRepoId)).withUser(fakeAccount).call()
      status(otherRepoRead) must equalTo(OK)
      contentAsString(otherRepoRead) must not contain
          repoRoutes.createDoc(otherRepoId).url

      // Now create a documentary unit...
      val docData = Map(
        "identifier" -> Seq("testdoc"),
        "descriptions[0].languageCode" -> Seq("eng"),
        "descriptions[0].identityArea.name" -> Seq("A new document"),
        "descriptions[0].contentArea.scopeAndContent" -> Seq("Lots...")
      )

      val createDocPost = FakeRequest(repoRoutes.createDocPost(repoId))
        .withUser(fakeAccount).withCsrf.callWith(docData)
      status(createDocPost) must equalTo(SEE_OTHER)

      // Test we can read the new repository
      val docId = idFromUrl(redirectLocation(createDocPost).get)
      val docRead = FakeRequest(docRoutes.get(docId))
        .withUser(fakeAccount).call()
      status(docRead) must equalTo(OK)
      contentAsString(docRead) must contain("A new document")
      contentAsString(docRead) must contain(docRoutes.createDoc(docId).url)

      // Test we CAN'T create extra docs in an existing doc (c1)
      val otherDocRead = FakeRequest(docRoutes.get(otherDocId))
        .withUser(fakeAccount).call()
      status(otherDocRead) must equalTo(OK)
      contentAsString(otherDocRead) must not contain
          docRoutes.createDoc(otherDocId).url
    }
  }
}
