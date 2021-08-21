package integration.admin

import cookies.SessionPreferences
import helpers.IntegrationTestRunner
import models.{EntityType, _}
import play.api.test.FakeRequest
import services.data.{DataUser, AuthenticatedUser, HierarchyError}


class DocumentaryUnitViewsSpec extends IntegrationTestRunner {
  import mockdata.{privilegedUser, unprivilegedUser}

  private val docRoutes = controllers.units.routes.DocumentaryUnits
  private val repoRoutes = controllers.institutions.routes.Repositories

  // Common headers/strings
  val multipleItemsHeader = "Displaying items"
  val oneItemHeader = "One item found"
  val noItemsHeader = "No items found"

  "DocumentaryUnit views" should {

    "list should get some (world-readable) items" in new ITestApp {
      val list = FakeRequest(docRoutes.list()).withUser(unprivilegedUser).call()
      status(list) must equalTo(OK)
      contentAsString(list) must contain(multipleItemsHeader)
      contentAsString(list) must not contain "Documentary Unit 1"
      contentAsString(list) must contain("Documentary Unit 4")
      contentAsString(list) must contain("Documentary Unit m19")
    }

    "list when logged in should get more items" in new ITestApp {
      val list = FakeRequest(docRoutes.list()).withUser(privilegedUser).call()
      status(list) must equalTo(OK)
      contentAsString(list) must contain(multipleItemsHeader)
      contentAsString(list) must contain("Documentary Unit 1")
      contentAsString(list) must contain("Documentary Unit 2")
      contentAsString(list) must contain("Documentary Unit 3")
      contentAsString(list) must contain("Documentary Unit 4")
    }

    "search should find some items" in new ITestApp {
      val search = FakeRequest(docRoutes.search()).withUser(privilegedUser).call()
      status(search) must equalTo(OK)
      contentAsString(search) must contain(multipleItemsHeader)
      contentAsString(search) must contain("Documentary Unit 1")
      contentAsString(search) must contain("Documentary Unit 2")
      contentAsString(search) must contain("Documentary Unit 3")
      contentAsString(search) must contain("Documentary Unit 4")
    }

    "link to other privileged actions when logged in" in new ITestApp {
      val show = FakeRequest(docRoutes.get("c1")).withUser(privilegedUser).call()
      status(show) must equalTo(OK)
      contentAsString(show) must contain(docRoutes.update("c1").url)
      contentAsString(show) must contain(docRoutes.delete("c1").url)
      contentAsString(show) must contain(docRoutes.createDoc("c1").url)
      contentAsString(show) must contain(docRoutes.visibility("c1").url)
      contentAsString(show) must contain(docRoutes.search().url)
    }

    "link to holder" in new ITestApp {
      val show = FakeRequest(docRoutes.get("c1")).withUser(privilegedUser).call()
      status(show) must equalTo(OK)

      contentAsString(show) must contain(repoRoutes.get("r1").url)
    }

    "link to holder when a child item" in new ITestApp {
      val show = FakeRequest(docRoutes.get("c2")).withUser(privilegedUser).call()
      status(show) must equalTo(OK)

      contentAsString(show) must contain(repoRoutes.get("r1").url)
    }

    "show history when logged in as privileged user" in new ITestApp {
      val show = FakeRequest(docRoutes.history("c1")).withUser(privilegedUser).call()
      status(show) must equalTo(OK)
    }

    "throw a 404 when fetching items with the wrong type" in new ITestApp {
      // r1 is a repository, not a doc unit
      val show = FakeRequest(docRoutes.get("r1")).withUser(privilegedUser).call()
      status(show) must equalTo(NOT_FOUND)
    }

    "throw a 404 when fetching items with a dodgily encoded id" in new ITestApp {
      val show = FakeRequest(docRoutes.get("c1#desc-eng")).withUser(privilegedUser).call()
      status(show) must equalTo(NOT_FOUND)
    }

    "save visited items to the session" in new ITestApp {
      val show = FakeRequest(docRoutes.get("c1")).withUser(privilegedUser).call()
      session(show).get(SessionPreferences.DEFAULT_STORE_KEY) must beSome.which {
        _ must contain("c1")
      }
    }
  }

  "Documentary unit access functionality" should {

    "give access to c1 when logged in" in new ITestApp {
      val show = FakeRequest(docRoutes.get("c1")).withUser(privilegedUser).call()
      status(show) must equalTo(OK)
      contentAsString(show) must contain("c1")
    }

    "deny access to c2 when logged in as an ordinary user" in new ITestApp {
      val show = FakeRequest(docRoutes.get("c2")).withUser(unprivilegedUser).call()
      status(show) must equalTo(NOT_FOUND)
    }
  }

  "Documentary unit CRUD functionality" should {

    "show correct default values in the form when creating new items" in new ITestApp(
      Map("formConfig.DocumentaryUnit.rulesAndConventions.default" -> "SOME RANDOM VALUE")) {
      val form = FakeRequest(repoRoutes.createDoc("r1")).withUser(privilegedUser).call()
      status(form) must equalTo(OK)
      contentAsString(form) must contain("SOME RANDOM VALUE")
    }

    "NOT show default values in the form when editing items" in new ITestApp(
      Map("formConfig.DocumentaryUnit.rulesAndConventions.default" -> "SOME RANDOM VALUE")) {
      val form = FakeRequest(docRoutes.update("c1")).withUser(privilegedUser).call()
      status(form) must equalTo(OK)
      contentAsString(form) must not contain "SOME RANDOM VALUE"
    }

    "allow creating new items when logged in as privileged user" in new ITestApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("hello-kitty"),
        "descriptions[0].languageCode" -> Seq("eng"),
        "descriptions[0].identityArea.name" -> Seq("Hello Kitty"),
        "descriptions[0].contentArea.scopeAndContent" -> Seq("Some content"),
        "descriptions[0].identityArea.dates[0].startDate" -> Seq("1939-01-01"),
        "descriptions[0].identityArea.dates[0].endDate" -> Seq("1945-01-01"),
        "publicationStatus" -> Seq("Published")
      )
      val cr = FakeRequest(repoRoutes.createDocPost("r1"))
        .withUser(privilegedUser).withCsrf.callWith(testData)
      status(cr) must equalTo(SEE_OTHER)

      val show = FakeRequest(GET, redirectLocation(cr).get)
        .withUser(privilegedUser).call()
      status(show) must equalTo(OK)

      contentAsString(show) must contain("Some content")
      contentAsString(show) must contain("Held By")
      // After having created an item it should contain a 'history' pane
      // on the show page
      contentAsString(show) must contain(docRoutes.history("nl-r1-hello_kitty").url)
      indexEventBuffer.last must equalTo("nl-r1-hello_kitty")
    }

    "allow creating new items with non-ASCII identifiers" in new ITestApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("זהמזהה"),
        "descriptions[0].languageCode" -> Seq("heb"),
        "descriptions[0].identityArea.name" -> Seq("Hebrew Item")
      )
      val cr = FakeRequest(repoRoutes.createDocPost("r1"))
        .withUser(privilegedUser).withCsrf.callWith(testData)
      status(cr) must equalTo(SEE_OTHER)

      val show = FakeRequest(GET, redirectLocation(cr).get).withUser(privilegedUser).call()
      status(show) must equalTo(OK)

      contentAsString(show) must contain(docRoutes.history("nl-r1-זהמזהה").url)
      indexEventBuffer.last must equalTo("nl-r1-זהמזהה")
    }

    "allow creating new lower-level items" in new ITestApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("childitem"),
        "descriptions[0].languageCode" -> Seq("eng"),
        "descriptions[0].identityArea.name" -> Seq("Child Item")
      )
      val cr = FakeRequest(docRoutes.createDocPost("c1"))
        .withUser(privilegedUser)
        .withCsrf.callWith(testData)
      status(cr) must equalTo(SEE_OTHER)

      val show = FakeRequest(GET, redirectLocation(cr).get)
        .withUser(privilegedUser)
        .call()
      status(show) must equalTo(OK)

      contentAsString(show) must contain(docRoutes.history("nl-r1-c1-childitem").url)
      indexEventBuffer.last must equalTo("nl-r1-c1-childitem")
    }

    "allow creating new descriptions" in new ITestApp {
      val formReq = FakeRequest(docRoutes.createDescription("nl-r1-m19"))
        .withUser(privilegedUser)
        .withCsrf.call()

      val testData = formData(formReq)
        .updated("descriptions[1].languageCode", Seq("eng"))
        .updated("descriptions[1].identityArea.name", Seq("Duplicate ID"))

      val badReq = FakeRequest(docRoutes.createDescriptionPost("nl-r1-m19"))
        .withUser(privilegedUser)
        .withCsrf.callWith(testData)
      status(badReq) must_== BAD_REQUEST

      val fixedTestData = formData(badReq)
        .updated("descriptions[1].identifier", Seq("alt"))
        .updated("descriptions[1].identityArea.name", Seq("Fixed Duplicate ID"))

      val okReq = FakeRequest(docRoutes.createDescriptionPost("nl-r1-m19"))
        .withUser(privilegedUser)
        .withCsrf.callWith(fixedTestData)
      status(okReq) must_== SEE_OTHER

      val checkReq = FakeRequest(GET, redirectLocation(okReq).get)
        .withUser(privilegedUser)
        .call()
      contentAsString(checkReq) must contain("nl-r1-m19.eng-alt")
    }

    "give a form error when creating items with the same id as existing ones" in new ITestApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("c1"),
        "publicationStatus" -> Seq("Published")
      )
      // Since the item id is derived from the identifier field,
      // a form error should result from using the same identifier
      // twice within the given scope (in this case, r1)
      val call = FakeRequest(repoRoutes.createDocPost("r1")).withUser(privilegedUser).withCsrf
      val cr1 = call.callWith(testData)
      status(cr1) must equalTo(SEE_OTHER)
      // okay the first time
      val cr2 = call.callWith(testData)
      status(cr2) must equalTo(BAD_REQUEST)
      // NB: This error string comes from the server, so might
      // not match if changed there - single quotes surround the value
      contentAsString(cr2) must contain("exists and must be unique")

      // Submit a third time with extra @Relation data, as a test for issue #124
      val testData2 = testData ++ Map(
        "descriptions[0].languageCode" -> Seq("eng"),
        "descriptions[0].identityArea.name" -> Seq("Hello Kitty"),
        "descriptions[0].identityArea.dates[0].startDate" -> Seq("1939-01-01"),
        "descriptions[0].identityArea.dates[0].endDate" -> Seq("1945-01-01")
      )
      val cr3 = call.callWith(testData2)
      status(cr3) must equalTo(BAD_REQUEST)
      // NB: This error string comes from the server, so might
      // not match if changed there - single quotes surround the value
      contentAsString(cr3) must contain("exists and must be unique")
    }

    "give a form error when saving an item with with a bad date" in new ITestApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("c1"),
        "descriptions[0].identityArea.dates[0].startDate" -> Seq("1945-01-01"),
        "descriptions[0].identityArea.dates[0].endDate" -> Seq("1945-12-32") // BAD!
      )
      // Since the item id is derived from the identifier field,
      // a form error should result from using the same identifier
      // twice within the given scope (in this case, r1)
      val cr = FakeRequest(repoRoutes.createDocPost("r1"))
        .withUser(privilegedUser)
        .withCsrf
        .callWith(testData)
      status(cr) must equalTo(BAD_REQUEST)
      // If we were doing validating dates we'd use:
      contentAsString(cr) must contain(message("error.date"))
    }

    "allow updating items when logged in as privileged user" in new ITestApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("c1"),
        "descriptions[0].languageCode" -> Seq("eng"),
        "descriptions[0].identityArea.name" -> Seq("Collection 1"),
        "descriptions[0].identityArea.parallelFormsOfName[0]" -> Seq("Collection 1 Parallel Name"),
        "descriptions[0].contentArea.scopeAndContent" -> Seq("New Content for c1"),
        "descriptions[0].contextArea.acquistition" -> Seq("Acquisistion info"),
        "descriptions[0].notes[0]" -> Seq("Test Note"),
        "publicationStatus" -> Seq("Draft")
      )
      val cr = FakeRequest(docRoutes.updatePost("c1"))
        .withUser(privilegedUser)
        .withCsrf
        .callWith(testData)
      status(cr) must equalTo(SEE_OTHER)

      val show = FakeRequest(GET, redirectLocation(cr).get).withUser(privilegedUser).call()
      status(show) must equalTo(OK)
      contentAsString(show) must contain("Collection 1 Parallel Name")
      contentAsString(show) must contain("New Content for c1")
      contentAsString(show) must contain("Test Note")
      indexEventBuffer.last must equalTo("c1")
    }

    "allow updating an item with a custom log message" in new ITestApp {
      val msg = "Imma updating this item!"
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("c1"),
        "descriptions[0].languageCode" -> Seq("eng"),
        "descriptions[0].identityArea.name" -> Seq("Collection 1 - Updated"),
        "logMessage" -> Seq(msg)
      )
      val cr = FakeRequest(docRoutes.updatePost("c1")).withUser(privilegedUser)
        .withCsrf
        .callWith(testData)
      status(cr) must equalTo(SEE_OTHER)

      // Get the item history page and check the message is there...
      val show = FakeRequest(docRoutes.history("c1")).withUser(privilegedUser).call()
      status(show) must equalTo(OK)
      // Log message should be in the history section...
      contentAsString(show) must contain(msg)
      indexEventBuffer.last must equalTo("c1")
    }

    "prevent deleting c1 due to child items" in new ITestApp {
      val confirm = FakeRequest(docRoutes.delete("c1")).withUser(privilegedUser).call()
      status(confirm) must equalTo(OK)
      contentAsString(confirm) must contain(message("item.delete.childrenFirst", 1))
    }

    "error deleting c1 due to child items" in new ITestApp {
      await(FakeRequest(docRoutes.deletePost("c1"))
        .withUser(privilegedUser)
        .call()) must throwA[HierarchyError]
    }

    "allow deleting c4 when logged in" in new ITestApp {
      val del = FakeRequest(docRoutes.deletePost("c4"))
        .withUser(privilegedUser).call()
      status(del) must equalTo(SEE_OTHER)
    }

    "error deleting contents without confirmation" in new ITestApp {
      val data = Map(
        DeleteChildrenOptions.ALL -> Seq("true"),
        DeleteChildrenOptions.CONFIRM -> Seq("foo"),
        DeleteChildrenOptions.ANSWER -> Seq("bar")
      )
      val del = FakeRequest(docRoutes.deleteContentsPost("c1"))
        .withUser(privilegedUser)
        .callWith(data)
      status(del) must_== BAD_REQUEST
    }

    "error deleting contents without all option" in new ITestApp {
      val data = Map(
        DeleteChildrenOptions.ALL -> Seq("false"),
        DeleteChildrenOptions.CONFIRM -> Seq("foo"),
        DeleteChildrenOptions.ANSWER -> Seq("bar")
      )
      val del = FakeRequest(docRoutes.deleteContentsPost("c1"))
        .withUser(privilegedUser)
        .callWith(data)
      status(del) must_== BAD_REQUEST
    }

    "check deleting contents with a confirmation" in new ITestApp {
      val check = FakeRequest(docRoutes.deleteContents("c1"))
        .withUser(privilegedUser)
        .call()
      status(check) must_== OK

      // Check should contain a prompt like: `Type the following confirmation phrase: "delete 1 item"`
      val checkPhrase = message("item.deleteChildren.confirm", 1)
      val prompt = message("item.deleteChildren.confirmPhrase", checkPhrase)
        .replace("\"", "&quot;")
      contentAsString(check) must contain(prompt)
    }

    "allow deleting contents" in new ITestApp {
      val data = Map(
        DeleteChildrenOptions.ALL -> Seq("true"),
        DeleteChildrenOptions.CONFIRM -> Seq("foo"),
        DeleteChildrenOptions.ANSWER -> Seq("foo")
      )
      val del = FakeRequest(docRoutes.deleteContentsPost("c1"))
        .withUser(privilegedUser)
        .callWith(data)
      status(del) must_== SEE_OTHER
      flash(del).apply("success") must_== message("item.deleteChildren.confirmation", 2)
    }

    "allow renaming items" in new ITestApp {
      val testData: Map[String, Seq[String]] = Map("identifier" -> Seq("z2 / b"))
      val cr = FakeRequest(docRoutes.renamePost("c2"))
        .withUser(privilegedUser).callWith(testData)

      status(cr) must equalTo(SEE_OTHER)
      flash(cr).get("success") must beSome.which { m =>
        // We've renamed 2 items (c2) and its child, for
        // which 3 redirects are created each for a total of 6
        m must_== message("item.rename.confirmation", 6)
      }
      // Empty the mutable rename buffer so other tests are not
      // affected
      movedPages.clear()
    }

    "handle colliding renames" in new ITestApp {
      val testData: Map[String, Seq[String]] = Map("identifier" -> Seq("m19"))
      val cr = FakeRequest(docRoutes.renamePost("c1"))
        .withUser(privilegedUser).callWith(testData)
      status(cr) must equalTo(BAD_REQUEST)
    }

    "disallow updating items when logged in as unprivileged user" in new ITestApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("c4"),
        "descriptions[0].languageCode" -> Seq("eng"),
        "descriptions[0].identityArea.name" -> Seq("Collection 4"),
        "descriptions[0].contentArea.scopeAndContent" -> Seq("New Content for c4"),
        "publicationStatus" -> Seq("Draft")
      )

      val cr = FakeRequest(docRoutes.updatePost("c4"))
        .withUser(unprivilegedUser).callWith(testData)
      status(cr) must equalTo(FORBIDDEN)

      // We can view the item when not logged in...
      val show = FakeRequest(docRoutes.get("c4")).withUser(unprivilegedUser).call()
      status(show) must equalTo(OK)
      contentAsString(show) must not contain "New Content for c4"
      indexEventBuffer.last must not equalTo "c4"
    }

    "allow deleting access points" in new ITestApp {
      val testItem = "c1"
      val testItemDesc = "cd1"
      val testItemAp = "ur1"
      val get1 = FakeRequest(docRoutes.get(testItem)).withUser(privilegedUser).call()
      contentAsString(get1) must contain(testItemAp)
      // Now try again to update the item, which should succeed
      // Check we can update the item
      val cr = FakeRequest(docRoutes.deleteAccessPoint(testItem, testItemDesc, testItemAp))
        .withUser(privilegedUser).withCsrf.call()
      // NB: This is a JSON-only endpoint so it will give us OK instead
      // of a redirect
      status(cr) must equalTo(OK)
      val get2 = FakeRequest(docRoutes.get(testItem)).withUser(privilegedUser).call()
      status(get2) must equalTo(OK)
      indexEventBuffer.last must equalTo(testItem)
      contentAsString(get2) must not contain testItemAp
    }

    "allow updating visibility" in new ITestApp {
      val test1 = FakeRequest(docRoutes.get("c1")).withUser(unprivilegedUser).call()
      status(test1) must equalTo(NOT_FOUND)
      // Make item visible to user
      val data = Map(services.data.Constants.ACCESSOR_PARAM -> Seq(unprivilegedUser.id))
      val cr = FakeRequest(docRoutes.visibilityPost("c1"))
        .withUser(privilegedUser).withCsrf.callWith(data)
      status(cr) must equalTo(SEE_OTHER)
      val test2 = FakeRequest(docRoutes.get("c1")).withUser(unprivilegedUser).call()
      status(test2) must equalTo(OK)
    }

    "include description IDs in form when editing items" in new ITestApp {
      val form = FakeRequest(docRoutes.update("c1")).withUser(privilegedUser).call()
      val data = formData(contentAsString(form))
      // NB: description order is currently not determined...
      data.get("descriptions[0].id") must beOneOf(Some(Seq("cd1")), Some(Seq("cd1-2")))
      data.get("descriptions[1].id") must beOneOf(Some(Seq("cd1")), Some(Seq("cd1-2")))
    }

    "not change items when submitting an unedited form" in new ITestApp {
      implicit val apiUser: DataUser = AuthenticatedUser(privilegedUser.id)
      val c1 = await(dataApi.get[DocumentaryUnit]("c1"))
      val form = FakeRequest(docRoutes.update("c1")).withUser(privilegedUser).call()
      val data = formData(contentAsString(form))
      val cr = FakeRequest(docRoutes.updatePost("c1"))
        .withUser(privilegedUser)
        .withCsrf
        .callWith(data)
      status(cr) must equalTo(SEE_OTHER)
      val c2 = await(dataApi.get[DocumentaryUnit]("c1"))
      c2.data must_== c1.data
    }
  }
  
  "Documentary Unit link/annotate functionality" should {

    "contain correct access point links" in new ITestApp {
      val show = FakeRequest(docRoutes.get("c1")).withUser(privilegedUser).call()
      contentAsString(show) must contain("access-point-links")
      contentAsString(show) must contain(
        controllers.authorities.routes.HistoricalAgents.get("a1").url)
    }

    "contain correct annotation links" in new ITestApp {
      val show = FakeRequest(docRoutes.get("c1")).withUser(privilegedUser).call()
      contentAsString(show) must contain("annotation-links")
      contentAsString(show) must contain(
        docRoutes.get("c4").url)
    }

    "offer correct set of types to link against" in new ITestApp {
      val select = FakeRequest(docRoutes
        .linkAnnotateSelect("c1", EntityType.DocumentaryUnit)).withUser(privilegedUser).call()
      contentAsString(select) must contain(docRoutes
        .linkAnnotate("c1", EntityType.DocumentaryUnit, "c4").url)
      contentAsString(select) must not contain docRoutes
        .linkAnnotate("c1", EntityType.DocumentaryUnit, "a1").url
    }

    "allow linking to items via annotation" in new ITestApp {
      val testItem = "c1"
      val linkSrc = "cvocc1"
      val body = "This is a link"
      val testData: Map[String, Seq[String]] = Map(
        LinkF.LINK_TYPE -> Seq(LinkF.LinkType.Associative.toString),
        LinkF.DESCRIPTION -> Seq(body)
      )
      val cr = FakeRequest(docRoutes.linkAnnotatePost(testItem, EntityType.Concept, linkSrc))
        .withUser(privilegedUser).withCsrf.callWith(testData)
      status(cr) must equalTo(SEE_OTHER)
      val getR = FakeRequest(GET, redirectLocation(cr).get)
        .withUser(privilegedUser).call()
      status(getR) must equalTo(OK)
      contentAsString(getR) must contain(linkSrc)
      contentAsString(getR) must contain(body)
    }
  }

  "Documentary unit permissions functionality" should {

    "should redirect to login page when permission denied when not logged in" in new ITestApp {
      val show = FakeRequest(docRoutes.get("c1")).call()
      status(show) must equalTo(SEE_OTHER)
    }

    "allow granting permissions to create a doc within the scope of r2" in new ITestApp {

      val testRepo = "r2"
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("test"),
        "descriptions[0].languageCode" -> Seq("eng"),
        "descriptions[0].identityArea.name" -> Seq("Test Item"),
        "descriptions[0].contentArea.scopeAndContent" -> Seq("A test"),
        "publicationStatus" -> Seq("Draft")
      )

      // Trying to create the item should fail initially.
      // Check we cannot create an item...
      val cr = FakeRequest(repoRoutes.createDocPost("r2"))
        .withUser(unprivilegedUser).withCsrf.callWith(testData)
      status(cr) must equalTo(FORBIDDEN)

      // Grant permissions to create docs within the scope of r2
      val permTestData: Map[String, List[String]] = Map(
        ContentTypes.DocumentaryUnit.toString -> List("create", "update", "delete")
      )
      val permReq = FakeRequest(repoRoutes
          .setScopedPermissionsPost(testRepo, EntityType.UserProfile, unprivilegedUser.id))
        .withUser(privilegedUser).withCsrf.callWith(permTestData)
      status(permReq) must equalTo(SEE_OTHER)
      // Now try again and create the item... it should succeed.
      // Check we cannot create an item...
      val cr2 = FakeRequest(repoRoutes.createDocPost(testRepo))
        .withUser(unprivilegedUser).withCsrf.callWith(testData)
      status(cr2) must equalTo(SEE_OTHER)
      val getR = FakeRequest(GET, redirectLocation(cr2).get)
        .withUser(unprivilegedUser).withCsrf.call()
      status(getR) must equalTo(OK)
    }

    "allow granting permissions on a specific item" in new ITestApp {

      val testItem = "c4"
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq(testItem),
        "descriptions[0].languageCode" -> Seq("eng"),
        "descriptions[0].identityArea.name" -> Seq("Changed Name"),
        "descriptions[0].contentArea.scopeAndContent" -> Seq("A test"),
        "publicationStatus" -> Seq("Draft")
      )

      // Trying to create the item should fail initially.
      // Check we cannot create an item...
      val cr = FakeRequest(docRoutes.updatePost(testItem))
        .withUser(unprivilegedUser).withCsrf.callWith(testData)
      status(cr) must equalTo(FORBIDDEN)

      // Grant permissions to update item c1
      val permTestData: Map[String, List[String]] = Map(
        ContentTypes.DocumentaryUnit.toString -> List("update")
      )
      val permReq = FakeRequest(docRoutes
        .setItemPermissionsPost(testItem, EntityType.UserProfile, unprivilegedUser.id))
        .withUser(privilegedUser).withCsrf.callWith(permTestData)
      status(permReq) must equalTo(SEE_OTHER)
      // Now try again to update the item, which should succeed
      // Check we can update the item
      val cr2 = FakeRequest(docRoutes.updatePost(testItem))
        .withUser(unprivilegedUser).withCsrf.callWith(testData)
      status(cr2) must equalTo(SEE_OTHER)
      val getR = FakeRequest(GET, redirectLocation(cr2).get)
        .withUser(unprivilegedUser).call()
      status(getR) must equalTo(OK)
    }    
  }
}
