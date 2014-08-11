package models.json

import play.api.libs.json.{JsValue, Json}
import play.api.test.PlaySpecification
import models._
import scala.io.Source
import defines.EntityType

class JsonFormatSpec extends PlaySpecification {

  private def readResource(v: EntityType.Value): JsValue = readResource(v.toString + ".json")
  private def readResource(name: String): JsValue = Json.parse(helpers.resourceAsString(name))

  "Documentary Unit Format should read and write with no changes" in {
    val validation = readResource(EntityType.DocumentaryUnit).validate[DocumentaryUnitF]
    // The JSON should parse correctly
    validation.asEither must beRight
    val doc = validation.get

    // Writing the doc and reading it again should produce exactly
    // the same object
    doc mustEqual Json.toJson(doc).as[DocumentaryUnitF]

    // And error when parsing the wrong type
    val badParse = readResource(EntityType.Repository).validate[DocumentaryUnitF]
    badParse.asEither must beLeft
  }

  "Documentary Unit metadata should read correctly" in {
    val doc: DocumentaryUnit = readResource("documentaryUnit1.json").as[DocumentaryUnit]
    doc.holder must beSome
    doc.latestEvent must beSome
    doc.meta.value.get("childCount") must beSome
    doc.model.descriptions.headOption must beSome
  }

  "HistoricalAgent Format should read and write with no changes" in {
    val validation = readResource(EntityType.HistoricalAgent).validate[HistoricalAgentF]
    validation.asEither must beRight
    val actor = validation.get
    actor mustEqual Json.toJson(actor).as[HistoricalAgentF]
    val badParse = readResource(EntityType.Repository).validate[HistoricalAgentF]
    badParse.asEither must beLeft
  }

  "Repository Format should read and write with no changes" in {
    val validation = readResource(EntityType.Repository).validate[RepositoryF]
    validation.asEither must beRight
    val repo = validation.get
    repo mustEqual Json.toJson(repo).as[RepositoryF]
    val badParse = readResource(EntityType.HistoricalAgent).validate[RepositoryF]
    badParse.asEither must beLeft
  }

  "Repository metadata should read correctly" in {
    val repository: Repository = readResource("repository1.json").as[Repository]
    repository.country must beSome
    repository.latestEvent must beSome
    repository.meta.value.get("childCount") must beSome
    repository.model.descriptions.headOption must beSome.which { desc =>
      desc.maintenanceEvents.headOption must beSome
    }
  }

  "Concept Format should read and write with no changes" in {
    val validation = readResource(EntityType.Concept).validate[ConceptF]
    validation.asEither must beRight
    val concept = validation.get
    concept mustEqual Json.toJson(concept).as[ConceptF]
    val badParse = readResource(EntityType.Repository).validate[ConceptF]
    badParse.asEither must beLeft
  }

  "Vocabulary Format should read and write with no changes" in {
    val validation = readResource(EntityType.Vocabulary).validate[VocabularyF]
    validation.asEither must beRight
    val vocabulary = validation.get
    vocabulary mustEqual Json.toJson(vocabulary).as[VocabularyF]
    val badParse = readResource(EntityType.Repository).validate[VocabularyF]
    badParse.asEither must beLeft
  }

  "UserProfile Format should read and write with no changes" in {
    val validation = readResource(EntityType.UserProfile).validate[UserProfileF]
    // The JSON should parse correctly
    validation.asEither must beRight
    val user = validation.get
    user mustEqual Json.toJson(user).as[UserProfileF]
    val badParse = readResource(EntityType.Repository).validate[UserProfileF]
    badParse.asEither must beLeft
  }

  "Group Format should read and write with no changes" in {
    val validation = readResource(EntityType.Group).validate[GroupF]
    // The JSON should parse correctly
    validation.asEither must beRight
    val group = validation.get
    group mustEqual Json.toJson(group).as[GroupF]
    val badParse = readResource(EntityType.Repository).validate[GroupF]
    badParse.asEither must beLeft
  }

  "Annotation Format should read and write with no changes" in {
    val validation = readResource(EntityType.Annotation).validate[AnnotationF]
    // The JSON should parse correctly
    validation.asEither must beRight
    val annotation = validation.get
    annotation mustEqual Json.toJson(annotation).as[AnnotationF]
    val badParse = readResource(EntityType.Repository).validate[AnnotationF]
    badParse.asEither must beLeft
  }

  "Virtual Unit Format should read correctly" in {
    val validation = readResource(EntityType.VirtualUnit).validate[VirtualUnit]
    // The JSON should parse correctly
    validation.asEither must beRight
    val virtualUnit = validation.get
    virtualUnit.includedUnits.headOption must beSome

    val validation2 = readResource(EntityType.VirtualUnit.toString + "NoDesc.json").validate[VirtualUnit]
    // The JSON should parse correctly
    validation2.asEither must beRight
    val virtualUnit2 = validation2.get
    virtualUnit2.includedUnits.headOption must beNone
  }

  "Content type format should read and write correctly" in {
    val validation = readResource(EntityType.ContentType).validate[ContentType](ContentType.reads)
    validation.asEither must beRight
  }
}