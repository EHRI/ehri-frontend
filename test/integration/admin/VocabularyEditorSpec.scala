package integration.admin

import helpers.IntegrationTestRunner
import models.EntityType
import play.api.libs.json.{JsString, Json}
import play.api.test.FakeRequest


class VocabularyEditorSpec extends IntegrationTestRunner {
  import mockdata.privilegedUser

  private val veRoutes = controllers.vocabularies.routes.VocabularyEditor
  private val testConcept = Json.obj(
    "identifier" -> "cvocc3",
    "isA" -> EntityType.Concept.toString,
    "seeAlso" -> Json.arr(),
    "broaderTerms" -> Json.arr(),
    "descriptions" -> Json.arr(),
    "meta" -> Json.obj()
  )

  "VocabularyEditor methods" should {

    "show the app page" in new ITestApp {
      val page = FakeRequest(veRoutes.editor("cvoc1")).withUser(privilegedUser).call()
      status(page) must_== OK
    }

    "list available languages" in new ITestApp {
      val langs = FakeRequest(veRoutes.langs("cvoc1")).withUser(privilegedUser).call()
      (contentAsJson(langs) \ "data" \ 0 \ 0).asOpt[String] must beSome("eng")
    }

    "list top level items" in new ITestApp {
      val list = FakeRequest(veRoutes.list("cvoc1")).withUser(privilegedUser).call()
      (contentAsJson(list) \ "data" \ 0 \ 0).asOpt[String] must beSome("cvocc1")
    }

    "list narrower items" in new ITestApp {
      val children = FakeRequest(veRoutes.narrower("cvoc1", "cvocc1")).withUser(privilegedUser).call()
      (contentAsJson(children) \ "data" \ 0 \ 0).asOpt[String] must beSome("cvocc2")
    }

    "get next numeric identifier" in new ITestApp {
      val ident = FakeRequest(veRoutes.nextIdentifier("cvoc1")).withUser(privilegedUser).call()
      contentAsJson(ident) must_== JsString("3")
    }

    "create new items" in new ITestApp {
      val item = FakeRequest(veRoutes.createItem("cvoc1")).withUser(privilegedUser).callWith(testConcept)
      status(item) must_== CREATED
      (contentAsJson(item) \ "identifier").asOpt[String] must beSome("cvocc3")
    }

    "update items" in new ITestApp {
      val item = FakeRequest(veRoutes.updateItem("cvoc1", "cvocc2"))
        .withUser(privilegedUser).callWith(testConcept ++ Json.obj("id" -> "cvocc2"))
      status(item) must_== OK
      (contentAsJson(item) \ "identifier").asOpt[String] must beSome("cvocc3")
    }

    "set broader terms" in new ITestApp {
      val item = FakeRequest(veRoutes.createItem("cvoc1")).withUser(privilegedUser).callWith(testConcept)
      status(item) must_== CREATED
      val cid = (contentAsJson(item) \ "id").as[String]

      val update = FakeRequest(veRoutes.broader("cvoc1", cid))
        .withUser(privilegedUser)
        .callWith(Json.arr("cvocc1"))
      status(update) must_== OK
      (contentAsJson(update) \ "broaderTerms" \ 0 \ "id").asOpt[String] must beSome("cvocc1")
    }

    "delete items" in new ITestApp {
      val del = FakeRequest(veRoutes.deleteItem("cvoc1", "cvocc2")).withUser(privilegedUser).call()
      status(del) must_== NO_CONTENT
    }
  }
}
