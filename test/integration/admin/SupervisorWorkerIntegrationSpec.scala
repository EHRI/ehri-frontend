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
 *  - create a new supervisor group
 *  - create a new worker group
 *  - assign permissions to supervisor group to create/update/delete all items in a repo
 *  - assign permissions to worker group to create/update/delete only their OWN items in a repo
 *  - check that these perms are respected
 */
class SupervisorWorkerIntegrationSpec extends IntegrationTestRunner {
  import mockdata.privilegedUser

  implicit val apiUser: DataUser = DataUser(Some(privilegedUser.id))

  private val docRoutes = controllers.units.routes.DocumentaryUnits
  private val repoRoutes = controllers.institutions.routes.Repositories
  private val groupRoutes = controllers.groups.routes.Groups

  /**
   * Get the id from an URL where the ID is the last component...
   */
  private def idFromUrl(url: String) = url.substring(url.lastIndexOf("/") + 1)

  "The application" should {

    "support supervisor CUD and user COD" in new ITestApp {

      // Target country
      val countryId = "gb"

      // Create a new subject repository as the admin user
      val repoData = Map(
        "identifier" -> Seq("testrepo"),
        "descriptions[0].languageCode" -> Seq("eng"),
        "descriptions[0].name" -> Seq("A Test Repository"),
        "descriptions[0].descriptionArea.history" -> Seq("A repository with a long history")
      )
      val repoCreatePost = FakeRequest(controllers.countries.routes.Countries.createRepositoryPost(countryId))
        .withUser(privilegedUser).withCsrf.callWith(repoData)
      status(repoCreatePost) must equalTo(SEE_OTHER)

      // Test we can read the new repository
      val repoId = idFromUrl(redirectLocation(repoCreatePost).get)

      val repoRead = FakeRequest(controllers.institutions.routes.Repositories.get(repoId))
        .withUser(privilegedUser).call()
      status(repoRead) must equalTo(OK)
      contentAsString(repoRead) must contain("A Test Repository")

      // Create a new group to represent the *head archivist role*
      val headArchivistsGroupId = "headarchivists"
      val headArchivistsGroupData = Map(
        "identifier" -> Seq(headArchivistsGroupId),
        "name" -> Seq("Test Repo Head Archivists"),
        "description" -> Seq("Group for the Head Archivists in Test Repo")
      )
      val headArchivistsGroupCreatePost = FakeRequest(groupRoutes.createPost())
        .withUser(privilegedUser).withCsrf.callWith(headArchivistsGroupData)
      status(headArchivistsGroupCreatePost) must equalTo(SEE_OTHER)

      val archivistsGroupId = "archivists"
      val archivistsGroupData = Map(
        "identifier" -> Seq(archivistsGroupId),
        "name" -> Seq("Test Repo Archivists"),
        "description" -> Seq("Group for the Archivists in Test Repo")
      )
      val archivistsGroupCreatePost = FakeRequest(groupRoutes.createPost())
        .withUser(privilegedUser).withCsrf.callWith(archivistsGroupData)
      status(archivistsGroupCreatePost) must equalTo(SEE_OTHER)

      // Check we can read both groups
      val groupRead1 = FakeRequest(groupRoutes.get(headArchivistsGroupId)).withUser(privilegedUser).call()
      status(groupRead1) must equalTo(OK)

      val groupRead2 = FakeRequest(groupRoutes.get(archivistsGroupId)).withUser(privilegedUser).call()
      status(groupRead2) must equalTo(OK)

      // Grant scoped permissions for the head archivists to create, update, and delete
      // documentary units with repository repoId
      val haPermissionsToGrant = List(
        PermissionType.Create, PermissionType.Update, PermissionType.Delete, PermissionType.Annotate
      )
      val haPermData: Map[String, List[String]] = Map(
        EntityType.DocumentaryUnit.toString -> haPermissionsToGrant.map(_.toString)
      )
      val haPermSetPost = FakeRequest(repoRoutes.setScopedPermissionsPost(repoId, EntityType.Group, headArchivistsGroupId))
        .withUser(privilegedUser).withCsrf.callWith(haPermData)
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
      val aPermSetPost = FakeRequest(controllers.institutions.routes.Repositories.setScopedPermissionsPost(repoId, EntityType.Group, archivistsGroupId))
        .withUser(privilegedUser).withCsrf.callWith(aPermData)
      status(aPermSetPost) must equalTo(SEE_OTHER)


      // Okay, now create a new user and add them to the head-archivists group. Do this
      // in one go using the groups parameter
      val headArchivistUserId = "headarchivistuser"
      val haUserData = Map(
        "identifier" -> Seq(headArchivistUserId),
        "name" -> Seq("Bob Important"),
        "email" -> Seq("head-archivist@example.com"),
        "password" -> Seq("changeme"),
        "confirm" -> Seq("changeme"),
        "group[]" -> Seq(headArchivistsGroupId, archivistsGroupId) // NB: Note brackets on param name!!!
      )
      val haUserCreatePost = FakeRequest(controllers.users.routes.UserProfiles.createUserPost())
        .withUser(privilegedUser).withCsrf.callWith(haUserData)
      status(haUserCreatePost) must equalTo(SEE_OTHER)

      // Check we can read the user's page
      val haUserRead =  FakeRequest(controllers.users.routes.UserProfiles.get(headArchivistUserId))
        .withUser(privilegedUser).call()
      status(haUserRead) must equalTo(OK)

      // Fetch the user's profile to perform subsequent logins
      val headArchivistProfile = await(dataApi.get[UserProfile](headArchivistUserId))

      // Add their account to the mocks
      val haAccount = Account(headArchivistUserId, "head-archivist@example.com",
          verified = true, staff = true)
      mockdata.accountFixtures += haAccount.id -> haAccount


      // Now create a new user and add them to the archivists group. Do this
      // in one go using the groups parameter
      val archivistUserId = "archivistuser1"
      val aUserData = Map(
        "identifier" -> Seq(archivistUserId),
        "name" -> Seq("Jim Nobody"),
        "email" -> Seq("archivist1@example.com"),
        "password" -> Seq("changeme"),
        "confirm" -> Seq("changeme"),
        "group[]" -> Seq(archivistsGroupId) // NB: Note brackets on param name!!!
      )
      val aUserCreatePost = FakeRequest(controllers.users.routes.UserProfiles.createUserPost())
        .withUser(privilegedUser).withCsrf.callWith(aUserData)
      status(aUserCreatePost) must equalTo(SEE_OTHER)

      // Check we can read the user's page
      val aUserRead =  FakeRequest(controllers.users.routes.UserProfiles.get(archivistUserId))
        .withUser(privilegedUser).call()
      status(aUserRead) must equalTo(OK)

      // Fetch the user's profile to perform subsequent logins
      val archivistProfile = await(dataApi.get[UserProfile](archivistUserId))

      // Add the archivists group to the account mocks
      val aAccount = Account(archivistUserId, "archivist1@example.com",
        verified = true, staff = true)
      mockdata.accountFixtures += aAccount.id -> aAccount


      // Check each user can read their profile as themselves...
      val haUserReadAsSelf =  FakeRequest(controllers.users.routes.UserProfiles.get(headArchivistUserId))
        .withUser(haAccount).call()
      status(haUserReadAsSelf) must equalTo(OK)

      val aUserReadAsSelf =  FakeRequest(controllers.users.routes.UserProfiles.get(archivistUserId))
        .withUser(aAccount).call()
      status(aUserReadAsSelf) must equalTo(OK)

      // Test the Head Archivist can Create, Update, and Delete documentary units within repoId
      // Now create a documentary unit...
      val doc1Data = Map(
        "identifier" -> Seq("testdoc1"),
        "descriptions[0].languageCode" -> Seq("eng"),
        "descriptions[0].identityArea.name" -> Seq("A new document, made by the head archivist"),
        "descriptions[0].contentArea.scopeAndContent" -> Seq("Lots of stuff...")
      )

      val createDoc1Post = FakeRequest(controllers.institutions.routes.Repositories.createDocPost(repoId))
        .withUser(haAccount).withCsrf.callWith(doc1Data)
      status(createDoc1Post) must equalTo(SEE_OTHER)

      // Test we can read the new repository
      val doc1Id = idFromUrl(redirectLocation(createDoc1Post).get)
      val doc1Read = FakeRequest(docRoutes.get(doc1Id)).withUser(haAccount).call()
      status(doc1Read) must equalTo(OK)
      contentAsString(doc1Read) must contain("A new document")
      contentAsString(doc1Read) must contain(controllers.units
        .routes.DocumentaryUnits.createDoc(doc1Id).url)

      val doc1UpdateData = Map(
        "identifier" -> Seq("testdoc1"),
        "descriptions[0].languageCode" -> Seq("eng"),
        "descriptions[0].identityArea.name" -> Seq("A different name"),
        "descriptions[0].contentArea.scopeAndContent" -> Seq("Lots of stuff...")
      )

      val updateDoc1Post = FakeRequest(docRoutes.updatePost(doc1Id))
        .withUser(haAccount).withCsrf.callWith(doc1UpdateData)
      status(updateDoc1Post) must equalTo(SEE_OTHER)

      // Test the update doc contains the new info...
      val doc1UpdateRead = FakeRequest(docRoutes.get(doc1Id)).withUser(haAccount).call()
      status(doc1UpdateRead) must equalTo(OK)
      contentAsString(doc1UpdateRead) must contain("A different name")

      // Test we can delete the new document...
      val doc1DeleteRead = FakeRequest(docRoutes.deletePost(doc1Id)).withUser(haAccount).call()
      status(doc1DeleteRead) must equalTo(SEE_OTHER)
      val doc1CheckDeleteRead = FakeRequest(docRoutes.get(doc1Id)).withUser(haAccount).call()
      status(doc1CheckDeleteRead) must equalTo(NOT_FOUND)

      // ---------------------------------------------
      //
      // Test the Archivist can Create, Update, and Delete documentary units within repoId
      // She should be able to Update them because she owns them...
      val doc2Data = Map(
        "identifier" -> Seq("testdoc2"),
        "descriptions[0].languageCode" -> Seq("eng"),
        "descriptions[0].identityArea.name" -> Seq("A new document, made by an ordinary schmo archivist"),
        "descriptions[0].contentArea.scopeAndContent" -> Seq("Lots of stuff...")
      )

      val createDoc2Post = FakeRequest(controllers.institutions.routes.Repositories.createDocPost(repoId))
        .withUser(aAccount).withCsrf.callWith(doc2Data)
      status(createDoc2Post) must equalTo(SEE_OTHER)

      // Test we can read the new repository
      val doc2Id = idFromUrl(redirectLocation(createDoc2Post).get)
      val doc2Read = FakeRequest(docRoutes.get(doc2Id)).withUser(aAccount).call()
      status(doc2Read) must equalTo(OK)
      contentAsString(doc2Read) must contain("A new document")
      contentAsString(doc2Read) must contain(docRoutes.createDoc(doc2Id).url)

      val doc2UpdateData = Map(
        "identifier" -> Seq("testdoc2"),
        "descriptions[0].languageCode" -> Seq("eng"),
        "descriptions[0].identityArea.name" -> Seq("A different name"),
        "descriptions[0].contentArea.scopeAndContent" -> Seq("Lots of stuff...")
      )

      val updateDoc2Post = FakeRequest(docRoutes.updatePost(doc2Id))
        .withUser(aAccount).withCsrf.callWith(doc2UpdateData)
      status(updateDoc2Post) must equalTo(SEE_OTHER)

      // Test the update doc contains the new info...
      val doc2UpdateRead = FakeRequest(docRoutes.get(doc2Id)).withUser(aAccount).call()
      status(doc2UpdateRead) must equalTo(OK)
      contentAsString(doc2UpdateRead) must contain("A different name")

      // Test we can delete the new document...
      val doc2DeleteRead = FakeRequest(docRoutes.deletePost(doc2Id)).withUser(aAccount).call()
      status(doc2DeleteRead) must equalTo(SEE_OTHER)
      val doc2CheckDeleteRead = FakeRequest(docRoutes.get(doc2Id)).withUser(aAccount).call()
      status(doc2CheckDeleteRead) must equalTo(NOT_FOUND)

      // HOORAY! Basic stuff seems to work - now onto the difficult things...
      // Create a doc as the head archivist, then check it can't be deleted
      // by the pleb....
      //
      // Test the Head Archivist can Create a documentary units within repoId
      // and the ordinary archivist can't delete it...
      val doc3Data = Map(
        "identifier" -> Seq("testdoc3"),
        "descriptions[0].languageCode" -> Seq("eng"),
        "descriptions[0].identityArea.name" -> Seq("Another new document, made by the head archivist"),
        "descriptions[0].contentArea.scopeAndContent" -> Seq("Lots of stuff...")
      )

      val createDoc3Post = FakeRequest(controllers.institutions.routes.Repositories.createDocPost(repoId))
        .withUser(haAccount).withCsrf.callWith(doc3Data)
      status(createDoc3Post) must equalTo(SEE_OTHER)

      // Test we can read the new doc...
      val doc3Id = idFromUrl(redirectLocation(createDoc3Post).get)
      val doc3Read = FakeRequest(docRoutes.get(doc3Id)).withUser(haAccount).call()
      status(doc3Read) must equalTo(OK)
      contentAsString(doc3Read) must contain("Another new document")
      contentAsString(doc3Read) must contain(controllers.units
        .routes.DocumentaryUnits.createDoc(doc3Id).url)

      // Test for #131 - UI shows item can be edited 'cos it can't. This is a dataApi
      // bug but test we're not seeing it here.
      // The regular archivist should not see a link suggesting he can edit the item
      // NB: This was accompanied by a bug in the tests below, which were
      // throwing a PermissionError instead of returning a 401 Unauthorized!
      val doc3Read2 = FakeRequest(GET, docRoutes.get(doc3Id).url).withUser(aAccount).call()
      contentAsString(doc3Read2) must not contain docRoutes.update(doc3Id).url

      // Now ensure the ordinary archivist cannot update it!
      val doc3UpdateRead = FakeRequest(docRoutes.updatePost(doc3Id))
        .withUser(aAccount).withCsrf.callWith(doc3Data.updated("descriptions[0].identityArea.name", Seq("Foobar")))
      status(doc3UpdateRead) must equalTo(FORBIDDEN)

      // Now ensure the ordinary archivist cannot delete it!
      val doc3DeleteRead = FakeRequest(docRoutes.deletePost(doc3Id)).withUser(aAccount).call()
      status(doc3UpdateRead) must equalTo(FORBIDDEN)

      // Test the ordinary archivist can Create a documentary units within repoId
      // and the head archivist CAN delete it...
      val doc4Data = Map(
        "identifier" -> Seq("testdoc4"),
        "descriptions[0].languageCode" -> Seq("eng"),
        "descriptions[0].identityArea.name" -> Seq("Another new document, made by the head archivist"),
        "descriptions[0].contentArea.scopeAndContent" -> Seq("Lots of stuff...")
      )

      val createDoc4Post = FakeRequest(controllers.institutions.routes.Repositories.createDocPost(repoId))
        .withUser(aAccount).withCsrf.callWith(doc4Data)
      status(createDoc4Post) must equalTo(SEE_OTHER)

      // Test we can read the new repository
      val doc4Id = idFromUrl(redirectLocation(createDoc4Post).get)
      val doc4Read = FakeRequest(docRoutes.get(doc4Id)).withUser(aAccount).call()
      status(doc4Read) must equalTo(OK)
      contentAsString(doc4Read) must contain("Another new document")
      contentAsString(doc4Read) must contain(docRoutes.createDoc(doc4Id).url)

      // Now ensure the head archivist CAN update it!
      val doc4UpdateRead = FakeRequest(docRoutes.updatePost(doc4Id))
        .withUser(haAccount).withCsrf
        .callWith(doc4Data.updated("descriptions[0].identityArea.name", Seq("A different name")))
      status(doc4UpdateRead) must equalTo(SEE_OTHER)

     // Now ensure the head archivist CAN delete it!
      val doc4DeleteRead = FakeRequest(docRoutes.deletePost(doc4Id)).withUser(haAccount).call()
      status(doc4DeleteRead) must equalTo(SEE_OTHER)
    }
  }
}
