package test.json

import play.api.libs.json.Json
import play.api.test.PlaySpecification
import models.{json => _, _}

class JsonFormatSpec extends PlaySpecification {

  "Documentary Unit Format should read and write with no changes" in {
    import models.json.DocumentaryUnitFormat._
    val validation = Json.parse(json.documentaryUnitTestJson).validate[DocumentaryUnitF]
    // The JSON should parse correctly
    validation.asEither must beRight
    val doc = validation.get

    // Writing the doc and reading it again should produce exactly
    // the same object
    doc mustEqual Json.toJson(doc).as[DocumentaryUnitF]

    // And error when parsing the wrong type
    val badParse = Json.parse(json.repoTestJson).validate[DocumentaryUnitF]
    badParse.asEither must beLeft
  }

  "HistoricalAgent Format should read and write with no changes" in {
    import models.json.HistoricalAgentFormat._
    val validation = Json.parse(json.actorTestJson).validate[HistoricalAgentF]
    validation.asEither must beRight
    val actor = validation.get
    actor mustEqual Json.toJson(actor).as[HistoricalAgentF]
    val badParse = Json.parse(json.repoTestJson).validate[HistoricalAgentF]
    badParse.asEither must beLeft
  }

  "Repository Format should read and write with no changes" in {
    import models.json.RepositoryFormat._
    val validation = Json.parse(json.repoTestJson).validate[RepositoryF]
    validation.asEither must beRight
    val repo = validation.get
    repo mustEqual Json.toJson(repo).as[RepositoryF]
    val badParse = Json.parse(json.documentaryUnitTestJson).validate[RepositoryF]
    badParse.asEither must beLeft
  }

  "Concept Format should read and write with no changes" in {
    import models.json.ConceptFormat._
    val validation = Json.parse(json.conceptTestJson).validate[ConceptF]
    validation.asEither must beRight
    val concept = validation.get
    concept mustEqual Json.toJson(concept).as[ConceptF]
    val badParse = Json.parse(json.repoTestJson).validate[ConceptF]
    badParse.asEither must beLeft
  }

  "Vocabulary Format should read and write with no changes" in {
    import models.json.VocabularyFormat._
    val validation = Json.parse(json.vocabTestJson).validate[VocabularyF]
    validation.asEither must beRight
    val vocabulary = validation.get
    vocabulary mustEqual Json.toJson(vocabulary).as[VocabularyF]
    val badParse = Json.parse(json.repoTestJson).validate[VocabularyF]
    badParse.asEither must beLeft
  }

  "UserProfile Format should read and write with no changes" in {
    import models.json.UserProfileFormat._
    val validation = Json.parse(json.userProfileTestJson).validate[UserProfileF]
    // The JSON should parse correctly
    validation.asEither must beRight
    val user = validation.get
    user mustEqual Json.toJson(user).as[UserProfileF]
    val badParse = Json.parse(json.repoTestJson).validate[UserProfileF]
    badParse.asEither must beLeft
  }

  "Group Format should read and write with no changes" in {
    import models.json.GroupFormat._
    val validation = Json.parse(json.groupTestJson).validate[GroupF]
    // The JSON should parse correctly
    validation.asEither must beRight
    val group = validation.get
    group mustEqual Json.toJson(group).as[GroupF]
    val badParse = Json.parse(json.repoTestJson).validate[GroupF]
    badParse.asEither must beLeft
  }

  "Annotation Format should read and write with no changes" in {
    import models.json.AnnotationFormat._
    val validation = Json.parse(json.annotationTestJson).validate[AnnotationF]
    // The JSON should parse correctly
    validation.asEither must beRight
    val annotation = validation.get
    annotation mustEqual Json.toJson(annotation).as[AnnotationF]
    val badParse = Json.parse(json.repoTestJson).validate[AnnotationF]
    badParse.asEither must beLeft
  }
}