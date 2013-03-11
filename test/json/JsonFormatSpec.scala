package test.json

import org.specs2.mutable._

import play.api.test._
import play.api.test.Helpers._
import play.api.i18n.Messages
import models.json._
import play.api.libs.json.Json
import models.{ConceptF, RepositoryF, ActorF, DocumentaryUnitF,VocabularyF,UserProfileF,GroupF}

class JsonFormatSpec extends Specification {

  "Documentary Unit Format should read and write with no changes" in {
    import models.json.DocumentaryUnitFormat._
    val validation = Json.parse(json.documentaryUnitTestJson).validate[DocumentaryUnitF]
    // The JSON should parse correctly
    validation.asEither must beRight
    val doc = validation.get

    // Writing the doc and reading it again should produce exactly
    // the same object
    doc mustEqual Json.toJson(doc).as[DocumentaryUnitF]
  }

  "Actor Format should read and write with no changes" in {
    import models.json.ActorFormat._
    val validation = Json.parse(json.actorTestJson).validate[ActorF]
    validation.asEither must beRight
    val actor = validation.get
    actor mustEqual Json.toJson(actor).as[ActorF]
  }

  "Repository Format should read and write with no changes" in {
    import models.json.RepositoryFormat._
    val validation = Json.parse(json.repoTestJson).validate[RepositoryF]
    validation.asEither must beRight
    val repo = validation.get
    repo mustEqual Json.toJson(repo).as[RepositoryF]
  }

  "Concept Format should read and write with no changes" in {
    import models.json.ConceptFormat._
    val validation = Json.parse(json.conceptTestJson).validate[ConceptF]
    validation.asEither must beRight
    val concept = validation.get
    concept mustEqual Json.toJson(concept).as[ConceptF]
  }

  "Vocabulary Format should read and write with no changes" in {
    import models.json.VocabularyFormat._
    val validation = Json.parse(json.vocabTestJson).validate[VocabularyF]
    validation.asEither must beRight
    val vocabulary = validation.get
    vocabulary mustEqual Json.toJson(vocabulary).as[VocabularyF]
  }

  "UserProfile Format should read and write with no changes" in {
    import models.json.UserProfileFormat._
    val validation = Json.parse(json.userProfileTestJson).validate[UserProfileF]
    // The JSON should parse correctly
    validation.asEither must beRight
    val user = validation.get
    user mustEqual Json.toJson(user).as[UserProfileF]
  }

  "Group Format should read and write with no changes" in {
    import models.json.GroupFormat._
    val validation = Json.parse(json.groupTestJson).validate[GroupF]
    // The JSON should parse correctly
    validation.asEither must beRight
    val group = validation.get
    group mustEqual Json.toJson(group).as[GroupF]
  }
}