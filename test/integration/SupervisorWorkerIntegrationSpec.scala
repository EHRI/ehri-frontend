package integration

import scala.concurrent.ExecutionContext.Implicits.global
import helpers._
import models.UserProfile
import defines._
import backend.rest.{PermissionDenied, ItemNotFound}
import models.MockAccount
import backend.ApiUser

/**
 * End-to-end test of the permissions system, implemented as one massive test.
 *
 * The purpose of this test is to:
 *
 *  - create a new supervisor group
 *  - create a new worker group
 *  - assign permissions to supervisor group to create/update/delete all items in a repo
 *  - assign permissions to worker group to create/update/delete only their OWN items in a repo
 *  - check that these perms are respected
 */
class SupervisorWorkerIntegrationSpec extends Neo4jRunnerSpec(classOf[SupervisorWorkerIntegrationSpec]) {
  import mocks.{privilegedUser,unprivilegedUser}

  implicit val apiUser: ApiUser = ApiUser(Some(privilegedUser.id))

  /**
   * Get the id from an URL where the ID is the last component...
   */
  private def idFromUrl(url: String) = url.substring(url.lastIndexOf("/") + 1)

  "The application" should {

    "support supervisor CUD and user COD" in new FakeApp {

      // Target country
      val countryId = "gb"

      // Create a new subject repository as the admin user
      val repoData = Map(
        "identifier" -> Seq("testrepo"),
        "descriptions[0].languageCode" -> Seq("en"),
        "descriptions[0].name" -> Seq("A Test Repository"),
        "descriptions[0].descriptionArea.history" -> Seq("A repository with a long history")
      )
      val repoCreatePost = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.archdesc.routes.Countries.createRepositoryPost(countryId).url)
        .withHeaders(formPostHeaders.toSeq: _*), repoData).get
      status(repoCreatePost) must equalTo(SEE_OTHER)

      // Test we can read the new repository
      val repoId = idFromUrl(redirectLocation(repoCreatePost).get)

      val repoRead = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
          controllers.archdesc.routes.Repositories.get(repoId).url)).get
      status(repoRead) must equalTo(OK)
      contentAsString(repoRead) must contain("A Test Repository")

      // Create a new group to represent the *head archivist role*
      val headArchivistsGroupId = "head-archivists"
      val headArchivistsGroupData = Map(
        "identifier" -> Seq(headArchivistsGroupId),
        "name" -> Seq("Test Repo Head Archivists"),
        "description" -> Seq("Group for the Head Archivists in Test Repo")
      )
      val headArchivistsGroupCreatePost = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.core.routes.Groups.create().url)
        .withHeaders(formPostHeaders.toSeq: _*), headArchivistsGroupData).get
      status(headArchivistsGroupCreatePost) must equalTo(SEE_OTHER)

      val archivistsGroupId = "archivists"
      val archivistsGroupData = Map(
        "identifier" -> Seq(archivistsGroupId),
        "name" -> Seq("Test Repo Archivists"),
        "description" -> Seq("Group for the Archivists in Test Repo")
      )
      val archivistsGroupCreatePost = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.core.routes.Groups.create().url)
        .withHeaders(formPostHeaders.toSeq: _*), archivistsGroupData).get
      status(archivistsGroupCreatePost) must equalTo(SEE_OTHER)

      // Check we can read both groups
      val groupRead1 = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        controllers.core.routes.Groups.get(headArchivistsGroupId).url)).get
      status(groupRead1) must equalTo(OK)

      val groupRead2 = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        controllers.core.routes.Groups.get(archivistsGroupId).url)).get
      status(groupRead2) must equalTo(OK)

      // Grant scoped permissions for the head archivists to create, update, and delete
      // documentary units with repository repoId
      val haPermissionsToGrant = List(
        PermissionType.Create, PermissionType.Update, PermissionType.Delete, PermissionType.Annotate
      )
      val haPermData: Map[String, List[String]] = Map(
        EntityType.DocumentaryUnit.toString -> haPermissionsToGrant.map(_.toString)
      )
      val haPermSetPost = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
          controllers.archdesc.routes.Repositories.setScopedPermissionsPost(repoId, EntityType.Group, headArchivistsGroupId).url)
      .withHeaders(formPostHeaders.toSeq: _*), haPermData).get
      status(haPermSetPost) must equalTo(SEE_OTHER)

      // Grant scoped permissions for the archivists to create and delete
      // documentary units with repository repoId (NOT update or delete -
      // these will be provided on the user's own docs with the implicit Owner perm)
      val aPermissionsToGrant = List(
        PermissionType.Create, PermissionType.Annotate
      )
      val aPermData: Map[String, List[String]] = Map(
        EntityType.DocumentaryUnit.toString -> aPermissionsToGrant.map(_.toString)
      )
      val aPermSetPost = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
          controllers.archdesc.routes.Repositories.setScopedPermissionsPost(repoId, EntityType.Group, archivistsGroupId).url)
      .withHeaders(formPostHeaders.toSeq: _*), aPermData).get
      status(aPermSetPost) must equalTo(SEE_OTHER)


      // Okay, now create a new user and add them to the head-archivists group. Do this
      // in one go using the groups parameter
      val headArchivistUserId = "head-archivist-user"
      val haUserData = Map(
        "identifier" -> Seq(headArchivistUserId),
        "name" -> Seq("Bob Important"),
        "email" -> Seq("head-archivist@example.com"),
        "password" -> Seq("changeme"),
        "confirm" -> Seq("changeme"),
        "group[]" -> Seq(headArchivistsGroupId, archivistsGroupId) // NB: Note brackets on param name!!!
      )
      val haUserCreatePost = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.core.routes.Admin.createUserPost().url)
        .withHeaders(formPostHeaders.toSeq: _*), haUserData).get
      status(haUserCreatePost) must equalTo(SEE_OTHER)

      // Check we can read the user's page
      val haUserRead =  route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        controllers.core.routes.UserProfiles.get(headArchivistUserId).url)).get
      status(haUserRead) must equalTo(OK)

      // Fetch the user's profile to perform subsequent logins
      val headArchivistProfile = await(testBackend.get[UserProfile](headArchivistUserId))

      // Add their account to the mocks
      val haAccount = MockAccount(headArchivistUserId, "head-archivist@example.com",
          verified = true, staff = true)
      mocks.userFixtures += haAccount.id -> haAccount


      // Now create a new user and add them to the archivists group. Do this
      // in one go using the groups parameter
      val archivistUserId = "archivist-user1"
      val aUserData = Map(
        "identifier" -> Seq(archivistUserId),
        "name" -> Seq("Jim Nobody"),
        "email" -> Seq("archivist1@example.com"),
        "password" -> Seq("changeme"),
        "confirm" -> Seq("changeme"),
        "group[]" -> Seq(archivistsGroupId) // NB: Note brackets on param name!!!
      )
      val aUserCreatePost = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.core.routes.Admin.createUserPost().url)
        .withHeaders(formPostHeaders.toSeq: _*), aUserData).get
      status(aUserCreatePost) must equalTo(SEE_OTHER)

      // Check we can read the user's page
      val aUserRead =  route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        controllers.core.routes.UserProfiles.get(archivistUserId).url)).get
      status(aUserRead) must equalTo(OK)

      // Fetch the user's profile to perform subsequent logins
      val archivistProfile = await(testBackend.get[UserProfile](archivistUserId))

      // Add the archivists group to the account mocks
      val aAccount = MockAccount(archivistUserId, "archivist1@example.com",
        verified = true, staff = true)
      mocks.userFixtures += aAccount.id -> aAccount


      // Check each user can read their profile as themselves...
      val haUserReadAsSelf =  route(fakeLoggedInHtmlRequest(haAccount, GET,
        controllers.core.routes.UserProfiles.get(headArchivistUserId).url)).get
      status(haUserReadAsSelf) must equalTo(OK)

      val aUserReadAsSelf =  route(fakeLoggedInHtmlRequest(aAccount, GET,
        controllers.core.routes.UserProfiles.get(archivistUserId).url)).get
      status(aUserReadAsSelf) must equalTo(OK)

      // Test the Head Archivist can Create, Update, and Delete documentary units within repoId
      // Now create a documentary unit...
      val doc1Data = Map(
        "identifier" -> Seq("testdoc1"),
        "descriptions[0].languageCode" -> Seq("en"),
        "descriptions[0].identityArea.name" -> Seq("A new document, made by the head archivist"),
        "descriptions[0].contentArea.scopeAndContent" -> Seq("Lots of stuff...")
      )

      val createDoc1Post = route(fakeLoggedInHtmlRequest(haAccount, POST,
        controllers.archdesc.routes.Repositories.createDocPost(repoId).url).withHeaders(formPostHeaders.toSeq: _*), doc1Data).get
      status(createDoc1Post) must equalTo(SEE_OTHER)

      // Test we can read the new repository
      val doc1Id = idFromUrl(redirectLocation(createDoc1Post).get)
      val doc1Read = route(fakeLoggedInHtmlRequest(haAccount, GET,
          controllers.archdesc.routes.DocumentaryUnits.get(doc1Id).url)).get
      status(doc1Read) must equalTo(OK)
      contentAsString(doc1Read) must contain("A new document")
      contentAsString(doc1Read) must contain(controllers.archdesc.routes.DocumentaryUnits.createDoc(doc1Id).url)

      val doc1UpdateData = Map(
        "identifier" -> Seq("testdoc1"),
        "descriptions[0].languageCode" -> Seq("en"),
        "descriptions[0].identityArea.name" -> Seq("A different name"),
        "descriptions[0].contentArea.scopeAndContent" -> Seq("Lots of stuff...")
      )

      val updateDoc1Post = route(fakeLoggedInHtmlRequest(haAccount, POST,
        controllers.archdesc.routes.DocumentaryUnits.update(doc1Id).url).withHeaders(formPostHeaders.toSeq: _*), doc1UpdateData).get
      status(updateDoc1Post) must equalTo(SEE_OTHER)

      // Test the update doc contains the new info...
      val doc1UpdateRead = route(fakeLoggedInHtmlRequest(haAccount, GET,
          controllers.archdesc.routes.DocumentaryUnits.get(doc1Id).url)).get
      status(doc1UpdateRead) must equalTo(OK)
      contentAsString(doc1UpdateRead) must contain("A different name")

      // Test we can delete the new document...
      val doc1DeleteRead = route(fakeLoggedInHtmlRequest(haAccount, POST,
          controllers.archdesc.routes.DocumentaryUnits.delete(doc1Id).url).withHeaders(formPostHeaders.toSeq: _*)).get
      status(doc1DeleteRead) must equalTo(SEE_OTHER)
      val doc1CheckDeleteRead = route(fakeLoggedInHtmlRequest(haAccount, GET,
          controllers.archdesc.routes.DocumentaryUnits.get(doc1Id).url)).get
      status(doc1CheckDeleteRead) must throwA[ItemNotFound]

      // ---------------------------------------------
      //
      // Test the Archivist can Create, Update, and Delete documentary units within repoId
      // She should be able to Update them because she owns them...
      val doc2Data = Map(
        "identifier" -> Seq("testdoc2"),
        "descriptions[0].languageCode" -> Seq("en"),
        "descriptions[0].identityArea.name" -> Seq("A new document, made by an ordinary schmo archivist"),
        "descriptions[0].contentArea.scopeAndContent" -> Seq("Lots of stuff...")
      )

      val createDoc2Post = route(fakeLoggedInHtmlRequest(aAccount, POST,
        controllers.archdesc.routes.Repositories.createDocPost(repoId).url).withHeaders(formPostHeaders.toSeq: _*), doc2Data).get
      status(createDoc2Post) must equalTo(SEE_OTHER)

      // Test we can read the new repository
      val doc2Id = idFromUrl(redirectLocation(createDoc2Post).get)
      val doc2Read = route(fakeLoggedInHtmlRequest(aAccount, GET,
          controllers.archdesc.routes.DocumentaryUnits.get(doc2Id).url)).get
      status(doc2Read) must equalTo(OK)
      contentAsString(doc2Read) must contain("A new document")
      contentAsString(doc2Read) must contain(controllers.archdesc.routes.DocumentaryUnits.createDoc(doc2Id).url)

      val doc2UpdateData = Map(
        "identifier" -> Seq("testdoc2"),
        "descriptions[0].languageCode" -> Seq("en"),
        "descriptions[0].identityArea.name" -> Seq("A different name"),
        "descriptions[0].contentArea.scopeAndContent" -> Seq("Lots of stuff...")
      )

      val updateDoc2Post = route(fakeLoggedInHtmlRequest(aAccount, POST,
        controllers.archdesc.routes.DocumentaryUnits.update(doc2Id).url).withHeaders(formPostHeaders.toSeq: _*), doc2UpdateData).get
      status(updateDoc2Post) must equalTo(SEE_OTHER)

      // Test the update doc contains the new info...
      val doc2UpdateRead = route(fakeLoggedInHtmlRequest(aAccount, GET,
          controllers.archdesc.routes.DocumentaryUnits.get(doc2Id).url)).get
      status(doc2UpdateRead) must equalTo(OK)
      contentAsString(doc2UpdateRead) must contain("A different name")

      // Test we can delete the new document...
      val doc2DeleteRead = route(fakeLoggedInHtmlRequest(aAccount, POST,
          controllers.archdesc.routes.DocumentaryUnits.delete(doc2Id).url).withHeaders(formPostHeaders.toSeq: _*)).get
      status(doc2DeleteRead) must equalTo(SEE_OTHER)
      val doc2CheckDeleteRead = route(fakeLoggedInHtmlRequest(aAccount, GET,
          controllers.archdesc.routes.DocumentaryUnits.get(doc2Id).url)).get
      status(doc2CheckDeleteRead) must throwA[ItemNotFound]

      // HOORAY! Basic stuff seems to work - now onto the difficult things...
      // Create a doc as the head archivist, then check it can't be deleted
      // by the pleb....
      //
      // Test the Head Archivist can Create a documentary units within repoId
      // and the ordinary archivist can't delete it...
      val doc3Data = Map(
        "identifier" -> Seq("testdoc3"),
        "descriptions[0].languageCode" -> Seq("en"),
        "descriptions[0].identityArea.name" -> Seq("Another new document, made by the head archivist"),
        "descriptions[0].contentArea.scopeAndContent" -> Seq("Lots of stuff...")
      )

      val createDoc3Post = route(fakeLoggedInHtmlRequest(haAccount, POST,
        controllers.archdesc.routes.Repositories.createDocPost(repoId).url).withHeaders(formPostHeaders.toSeq: _*), doc3Data).get
      status(createDoc3Post) must equalTo(SEE_OTHER)

      // Test we can read the new doc...
      val doc3Id = idFromUrl(redirectLocation(createDoc3Post).get)
      val doc3Read = route(fakeLoggedInHtmlRequest(haAccount, GET,
          controllers.archdesc.routes.DocumentaryUnits.get(doc3Id).url)).get
      status(doc3Read) must equalTo(OK)
      contentAsString(doc3Read) must contain("Another new document")
      contentAsString(doc3Read) must contain(controllers.archdesc.routes.DocumentaryUnits.createDoc(doc3Id).url)

      // Now ensure the ordinary archivist cannot update it!
      val doc3UpdateRead = route(fakeLoggedInHtmlRequest(aAccount, POST,
          controllers.archdesc.routes.DocumentaryUnits.update(doc3Id).url).withHeaders(formPostHeaders.toSeq: _*),
          doc3Data.updated("descriptions[0].identityArea.name", Seq("Foobar"))).get
      status(doc3UpdateRead) must throwA[PermissionDenied]

      // Now ensure the ordinary archivist cannot delete it!
      val doc3DeleteRead = route(fakeLoggedInHtmlRequest(aAccount, POST,
          controllers.archdesc.routes.DocumentaryUnits.delete(doc3Id).url).withHeaders(formPostHeaders.toSeq: _*)).get
      status(doc3DeleteRead) must throwA[PermissionDenied]

      // Test the ordinary archivist can Create a documentary units within repoId
      // and the head archivist CAN delete it...
      val doc4Data = Map(
        "identifier" -> Seq("testdoc4"),
        "descriptions[0].languageCode" -> Seq("en"),
        "descriptions[0].identityArea.name" -> Seq("Another new document, made by the head archivist"),
        "descriptions[0].contentArea.scopeAndContent" -> Seq("Lots of stuff...")
      )

      val createDoc4Post = route(fakeLoggedInHtmlRequest(aAccount, POST,
      controllers.archdesc.routes.Repositories.createDocPost(repoId).url).withHeaders(formPostHeaders.toSeq: _*), doc4Data).get
      status(createDoc4Post) must equalTo(SEE_OTHER)

      // Test we can read the new repository
      val doc4Id = idFromUrl(redirectLocation(createDoc4Post).get)
      val doc4Read = route(fakeLoggedInHtmlRequest(aAccount, GET,
          controllers.archdesc.routes.DocumentaryUnits.get(doc4Id).url)).get
      status(doc4Read) must equalTo(OK)
      contentAsString(doc4Read) must contain("Another new document")
      contentAsString(doc4Read) must contain(controllers.archdesc.routes.DocumentaryUnits.createDoc(doc4Id).url)

      // Now ensure the head archivist CAN update it!
      val doc4UpdateRead = route(fakeLoggedInHtmlRequest(haAccount, POST,
          controllers.archdesc.routes.DocumentaryUnits.update(doc4Id).url).withHeaders(formPostHeaders.toSeq: _*),
          doc4Data.updated("descriptions[0].identityArea.name", Seq("A different name"))).get
      status(doc4UpdateRead) must equalTo(SEE_OTHER)

     // Now ensure the head archivist CAN delete it!
      val doc4DeleteRead = route(fakeLoggedInHtmlRequest(haAccount, POST,
          controllers.archdesc.routes.DocumentaryUnits.delete(doc4Id).url).withHeaders(formPostHeaders.toSeq: _*)).get
      status(doc4DeleteRead) must equalTo(SEE_OTHER)

    }
  }
}
