package models.base

import play.api.test.PlaySpecification
import models.relation
import eu.ehri.project.definitions.Ontology
import play.api.libs.json.{Json, JsObject}
import defines.EntityType
import models.AccessPointF
import play.api.i18n.{MessagesApi, Messages, Lang}
import Description._
import backend.Entity

case class TestDescriptionF(
  id: Option[String],
  isA: EntityType.Value = EntityType.DocumentaryUnitDescription,
  name: String,
  languageCode: String,
  @relation(Ontology.HAS_ACCESS_POINT)
  creationProcess: CreationProcess.Value = CreationProcess.Manual,
  accessPoints: List[AccessPointF] = Nil,
  @relation(Ontology.HAS_ACCESS_POINT)
  unknownProperties: List[Entity] = Nil
) extends Model
  with Description {
  def toSeq = Seq.empty
  def displayText = None
}

case class TestModelF(
  id: Option[String] = None,
  isA: EntityType.Value = EntityType.DocumentaryUnit,
  @relation(Ontology.DESCRIPTION_FOR_ENTITY)
  descriptions: List[TestDescriptionF] = Nil
) extends Model
  with Described[TestDescriptionF]

case class TestModel(
  model: TestModelF,
  meta: JsObject = Json.obj()
) extends MetaModel[TestModelF]
  with DescribedMeta[TestDescriptionF, TestModelF]

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
class AnyModelSpec extends PlaySpecification with play.api.i18n.I18nSupport {

  implicit val application = new play.api.inject.guice.GuiceApplicationBuilder().build
  implicit val messagesApi = application.injector.instanceOf[MessagesApi]

  val testModel = TestModel(
    TestModelF(
      id = Some("id1"),
      descriptions = List(
        TestDescriptionF(
          id = Some("did1"),
          name = "name1",
          languageCode = "eng"
        ),
        TestDescriptionF(
          id = Some("did2"),
          name = "name2",
          languageCode = "fra"
        )
      )
    )
  )

  "AnyModel" should {
    "pick the right locale-dependent name" in {
      testModel.toStringLang(Messages(Lang("en"), messagesApi)) must equalTo("name1")
      testModel.toStringLang(Messages(Lang("fr"), messagesApi)) must equalTo("name2")
      testModel.toStringLang(Messages(Lang("en", "GB"), messagesApi)) must equalTo("name1")
    }

    "count descriptions properly" in {
      testModel.descriptions.size must equalTo(2)
    }
  }
}
