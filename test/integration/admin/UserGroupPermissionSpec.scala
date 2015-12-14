package integration.admin

import backend.ApiUser
import defines._
import helpers._
import models.{Account, Group, UserProfile}
import play.api.test.FakeRequest

/**
 * End-to-end test of the permissions system, implemented as one massive test.
 *
 * The purpose of this test is to:
 *
 *  - create a "user management" group
 *  - create a "note-approvers" group
 *  - give user-management grant permissions on note-approvers
 *  - give user-management update permissions on note-approvers
 *  - create a user1, belonging to user-management *and* note-approvers
 *  - create a user2
 *  - ensure user1 and add user2 to note-approvers, because:
 *    - user1 has update perms on the note-approvers group
 *    - user1 belongs to note-approvers herself
 */
class UserGroupPermissionSpec extends IntegrationTestRunner {
  import mockdata.privilegedUser

  implicit val apiUser: ApiUser = ApiUser(Some(privilegedUser.id))

  private val userRoutes = controllers.users.routes.UserProfiles
  private val groupRoutes = controllers.groups.routes.Groups

  private def createUser(id: String, data: Map[String, String], groups: Seq[String] = Seq.empty)(implicit app: play.api.Application): (Account, UserProfile) = {
    val userPostData: Map[String, Seq[String]] = data
      .map(kv => kv._1 -> Seq(kv._2))
      .updated("identifier", Seq(id))
      .updated("email", Seq(s"$id@example.com"))
      .updated("password", Seq("mypass"))
      .updated("confirm", Seq("mypass"))
      .updated("group[]", groups)

    val userCreatePost = FakeRequest(userRoutes.createUserPost())
      .withUser(privilegedUser).withCsrf.callWith(userPostData)
    status(userCreatePost) must equalTo(SEE_OTHER)
    redirectLocation(userCreatePost) must equalTo(Some(userRoutes.get(id).url))
    val acc: Account = await(mockAccounts.get(id))
    (acc, await(testBackend.get[UserProfile](id)))
  }

  private def createGroup(id: String, data: Map[String, String], groups: Seq[String] = Seq.empty)(implicit app: play.api.Application):
  Group = {
    val groupPostData: Map[String, Seq[String]] = data
      .map(kv => kv._1 -> Seq(kv._2))
      .updated("identifier", Seq(id))
      .updated("group[]", groups)

    val groupCreatePost = FakeRequest(groupRoutes.createPost())
      .withUser(privilegedUser).withCsrf.callWith(groupPostData)
    status(groupCreatePost) must equalTo(SEE_OTHER)
    redirectLocation(groupCreatePost) must equalTo(Some(groupRoutes.get(id).url))
    await(testBackend.get[Group](id))
  }

  "The application" should {

    "respect group/permission semantics" in new ITestApp {

      val management = createGroup("management", Map("name" -> "User Management"))
      val noteApprovers = createGroup("noteapprovers", Map("name" -> "Note Approvers"))
      val (acc1, user1) = createUser("user1", Map("name" -> "User 1"), Seq("management"))
      val (acc2, user2) = createUser("user2", Map("name" -> "User 2"))

      // Currently, user1 should **not** be able to add user2 to noteApprovers because
      // the management group does not have grant OR update permissions on that group
      val attempt1 = FakeRequest(userRoutes.addToGroup(user2.id, noteApprovers.id))
        .withUser(acc1).withCsrf.call()
      status(attempt1) must equalTo(FORBIDDEN)

      // Now set UPDATE permissions - this should still NOT be sufficient
      await(testBackend.setItemPermissions(management.id, ContentTypes.Group, noteApprovers.id,
        Seq(PermissionType.Update.toString)))

      val attempt2 = FakeRequest(userRoutes.addToGroup(user2.id, noteApprovers.id))
        .withUser(acc1).withCsrf.call()
      status(attempt2) must equalTo(FORBIDDEN)

      // Now set GRANT permissions on the user - this will still fail because
      // the user1 is not a member of noteApprovers
      await(testBackend.setItemPermissions(acc1.id, ContentTypes.UserProfile, acc2.id,
        Seq(PermissionType.Grant.toString)))

      // NB: Currently the front-end does not protect us against attempting
      // to add this user,
      val attempt3 = FakeRequest(userRoutes.addToGroup(acc2.id, noteApprovers.id))
        .withUser(acc1).withCsrf.call()
      status(attempt3) must equalTo(FORBIDDEN)

      await(testBackend.addGroup[Group, UserProfile](noteApprovers.id, acc1.id)) must beTrue

      val attempt4 = FakeRequest(userRoutes.addToGroup(acc2.id, noteApprovers.id))
        .withUser(acc1).withCsrf.call()
      status(attempt4) must equalTo(SEE_OTHER)
    }
  }
}
