package test

import helpers._
import models.UserProfile
import models.Entity
import models.base.Accessor
import controllers.routes
import play.api.test._
import play.api.test.Helpers._
import defines._
import rest.EntityDAO

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
class PermissionsIntegrationSpec extends Neo4jRunnerSpec(classOf[EntityViewsSpec]) {
  import mocks.UserFixtures.{privilegedUser,unprivilegedUser}

  val userProfile = UserProfile(Entity.fromString(privilegedUser.profile_id, EntityType.UserProfile)
    .withRelation(Accessor.BELONGS_REL, Entity.fromString("admin", EntityType.Group)))

  "Full-service permissions test should" should {

    "run correctly" in new FakeApp {

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
      val groupCreatePost = route(fakeLoggedInRequest(privilegedUser, POST,
        controllers.routes.Groups.create.url)
        .withHeaders(formPostHeaders.toSeq: _*), groupData).get
      status(groupCreatePost) must equalTo(SEE_OTHER)

      // Check we can read the group
      val groupRead = route(fakeLoggedInRequest(privilegedUser, GET,
        controllers.routes.Groups.get(groupId).url)).get
      status(groupRead) must equalTo(OK)

      // Grant scoped permissions for the group to create repos and docs in country gb
      val permissionsToGrant = List(
        PermissionType.Create, PermissionType.Update, PermissionType.Delete, PermissionType.Annotate
      )
      val permData: Map[String, List[String]] = Map(
        EntityType.Repository.toString -> permissionsToGrant.map(_.toString),
        EntityType.DocumentaryUnit.toString -> permissionsToGrant.map(_.toString)
      )
      val permSetPost = route(fakeLoggedInRequest(privilegedUser, POST,
          controllers.routes.Countries.setScopedPermissionsPost(countryId, EntityType.Group.toString, groupId).url)
      .withHeaders(formPostHeaders.toSeq: _*), permData).get
      status(permSetPost) must equalTo(SEE_OTHER)

      // Okay, now create a new user and add them to the uk-archivists group. Do this
      // in one go using the groups parameter
      val userId = "test-user"
      val newUserData = Map(
        "username" -> Seq(userId),
        "name" -> Seq("Test User"),
        "email" -> Seq("test-user@example.com"),
        "password" -> Seq("changeme"),
        "confirm" -> Seq("changeme"),
        "group[]" -> Seq(groupId) // NB: Note brackets on param name!!!
      )
      val userCreatePost = route(fakeLoggedInRequest(privilegedUser, POST,
        controllers.routes.Admin.createUserPost.url)
        .withHeaders(formPostHeaders.toSeq: _*), newUserData).get
      status(userCreatePost) must equalTo(SEE_OTHER)

      // Check we can read the user's page
      val userRead =  route(fakeLoggedInRequest(privilegedUser, GET,
        controllers.routes.UserProfiles.get(userId).url)).get
      status(userRead) must equalTo(OK)

      // Fetch the user's profile to perform subsequent logins
      val fetchProfile = await(rest.EntityDAO(EntityType.UserProfile, Some(userProfile)).get(userId))

      fetchProfile must beRight
      val profile = UserProfile(fetchProfile.right.get)
      println(profile)
      println(profile.groups)
      //println(await(rest.PermissionDAO(None).getScope(profile, countryId)).right.get)


      // TESTING MAGIC!!! We have to create an account for subsequent logins...
      // Then we add the account to the user fixtures (instead of adding it to the database,
      // which we don't have while testing.)
      val fakeAccount = new models.sql.OpenIDUser(1L, "test-user@example.com", userId)
      mocks.UserFixtures.all.append(fakeAccount)

      // Check the user can read their profile as themselves...
      // Check we can read the user's page
      val userReadAsSelf =  route(fakeLoggedInRequest(fakeAccount, GET,
        controllers.routes.UserProfiles.get(userId).url)).get
      status(userReadAsSelf) must equalTo(OK)

      // Now we're going to create a repository as the new user
      val repoData = Map(
        "identifier" -> Seq("testrepo"),
        "descriptions[0].languageCode" -> Seq("en"),
        "descriptions[0].name" -> Seq("A Test Repository"),
        "descriptions[0].descriptionArea.history" -> Seq("A repository with a long history")
      )
      val repoCreatePost = route(fakeLoggedInRequest(fakeAccount, POST,
        routes.Countries.createRepositoryPost(countryId).url)
        .withHeaders(formPostHeaders.toSeq: _*), repoData).get
      status(repoCreatePost) must equalTo(SEE_OTHER)

      // Test we can NOT create a repository in the other country...
      val otherRepoCreatePost = route(fakeLoggedInRequest(fakeAccount, POST,
        routes.Countries.createRepositoryPost(otherCountryId).url)
          .withHeaders(formPostHeaders.toSeq: _*), repoData).get
      status(otherRepoCreatePost) must equalTo(UNAUTHORIZED)


      // Test we can read the new repository
      val repoUrl = redirectLocation(repoCreatePost).get
      val repoId = repoUrl.substring(repoUrl.lastIndexOf("/") + 1)

      val repoRead = route(fakeLoggedInRequest(fakeAccount, GET,
          controllers.routes.Repositories.get(repoId).url)).get
      status(repoRead) must equalTo(OK)
      contentAsString(repoRead) must contain("A Test Repository")

      // Test we can create docs in this repository
      contentAsString(repoRead) must contain(controllers.routes.Repositories.createDoc(repoId).url)

      // Test we can NOT create docs in repository r1, which is in country NL
      val otherRepoRead = route(fakeLoggedInRequest(fakeAccount, GET,
          controllers.routes.Repositories.get(otherRepoId).url)).get
      status(otherRepoRead) must equalTo(OK)
      contentAsString(otherRepoRead) must not contain(controllers.routes.Repositories.createDoc(otherRepoId).url)

      // Now create a documentary unit...
      val docData = Map(
        "identifier" -> Seq("testdoc"),
        "descriptions[0].languageCode" -> Seq("en"),
        "descriptions[0].name" -> Seq("A new document"),
        "descriptions[0].contentArea.scopeAndContent" -> Seq("Lots...")
      )

      val createDocPost = route(fakeLoggedInRequest(fakeAccount, POST,
        routes.Repositories.createDocPost(repoId).url).withHeaders(formPostHeaders.toSeq: _*), docData).get
      status(createDocPost) must equalTo(SEE_OTHER)

      // Test we can read the new repository
      val docUrl = redirectLocation(createDocPost).get
      val docId = docUrl.substring(docUrl.lastIndexOf("/") + 1)
      println(docUrl)
      val docRead = route(fakeLoggedInRequest(fakeAccount, GET,
          controllers.routes.DocumentaryUnits.get(docId).url)).get
      status(docRead) must equalTo(OK)
      contentAsString(docRead) must contain("A new document")
      contentAsString(docRead) must contain(controllers.routes.DocumentaryUnits.createDoc(docId).url)

      // Test we CAN'T create extra docs in an existing doc (c1)
      println("Checking cannot create in other doc...")
      val otherDocRead = route(fakeLoggedInRequest(fakeAccount, GET,
          controllers.routes.DocumentaryUnits.get(otherDocId).url)).get
      status(otherDocRead) must equalTo(OK)
      contentAsString(otherDocRead) must not contain(controllers.routes.DocumentaryUnits.createDoc(otherDocId).url)

    }

  }

  step {
    runner.stop
  }
}
