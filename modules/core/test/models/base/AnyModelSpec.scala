package models.base

import models.AccessPointF.AccessPointType
import play.api.test.PlaySpecification
import models._
import eu.ehri.project.definitions.Ontology
import play.api.libs.json.{Json, JsObject}
import defines.EntityType
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
  accessPoints: Seq[AccessPointF] = Nil,
  @relation(Ontology.HAS_UNKNOWN_PROPERTY)
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

  val accessPoint1 = AccessPoint(model = AccessPointF(
    id = Some("ap1"),
    accessPointType = AccessPointF.AccessPointType.PersonAccess,
    name = "AP1"
  ))

  val accessPoint2 = AccessPoint(model = AccessPointF(
    id = Some("ap2"),
    accessPointType = AccessPointF.AccessPointType.CorporateBodyAccess,
    name = "AP2"
  ))

  val accessPoint3 = AccessPoint(model = AccessPointF(
    id = Some("ap3"),
    accessPointType = AccessPointF.AccessPointType.FamilyAccess,
    name = "AP3"
  ))

  val testModel = TestModel(
    TestModelF(
      id = Some("id1"),
      descriptions = List(
        TestDescriptionF(
          id = Some("did1"),
          name = "name1",
          languageCode = "eng",
          accessPoints = Seq(accessPoint1.model, accessPoint2.model)
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

    "categorise access point links properly" in {
      val links = Seq(
        Link(
          model = LinkF(
            id = Some("ln1"),
            linkType = LinkF.LinkType.Associative
          ),
          bodies = Seq(accessPoint1)
        )
      )
      testModel.accessPointLinks(links).headOption must beSome.which { case (link, ap) =>
        link.id must_== "ln1"
        ap.id must_== Some("ap1")
      }
      testModel.externalLinks(links) must beEmpty
      testModel.annotationLinks(links) must beEmpty
    }

    "categorise external links properly" in {
      val links = Seq(
        Link(
          model = LinkF(
            id = Some("ln1"),
            linkType = LinkF.LinkType.Associative
          ),
          bodies = Seq(accessPoint3)
        )
      )
      testModel.externalLinks(links).headOption must beSome.which { ext =>
        ext.id must_== "ln1"
      }
      testModel.accessPointLinks(links) must beEmpty
      testModel.annotationLinks(links) must beEmpty
    }

    "categorise annotation links properly" in {
      val links = Seq(
        Link(
          model = LinkF(
            id = Some("ln1"),
            linkType = LinkF.LinkType.Associative
          ),
          targets = Seq(accessPoint3)
        )
      )
      testModel.annotationLinks(links).headOption must beSome.which { ext =>
        ext.id must_== "ln1"
      }
      testModel.accessPointLinks(links) must beEmpty
      testModel.externalLinks(links) must beEmpty
    }

    "categorise access point links by type" in {
      val links = Seq(
        Link(
          model = LinkF(
            id = Some("ln1"),
            linkType = LinkF.LinkType.Associative
          ),
          bodies = Seq(accessPoint1)
        )
      )
      val byType = testModel.accessPointLinksByType(links)
      byType.size must_== 1
      byType.toSeq.headOption must beSome.which { case (t, aps) =>
        t must_== AccessPointF.AccessPointType.PersonAccess
        aps.headOption must beSome.which { case (link, ap) =>
          link.id must_== "ln1"
          ap.id must_== Some("ap1")
        }
      }
      testModel.annotationLinks(links) must beEmpty
      testModel.externalLinks(links) must beEmpty
    }
  }
}
