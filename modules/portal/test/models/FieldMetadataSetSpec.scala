package models

import play.api.test.PlaySpecification

import scala.collection.immutable.ListMap

class FieldMetadataSetSpec extends PlaySpecification {

  private val et = EntityType.RepositoryDescription
  private val fm1 = FieldMetadata(et, "1", "test1", Some("description"), Option.empty[FieldMetadata.Usage.Value], Some("info"), None, Seq("seeAlso"))
  private val fm2 = FieldMetadata(et, "2", "test2", Some("description"), Option.empty[FieldMetadata.Usage.Value], None, None, Seq("seeAlso"))
  private val templates = Seq("" -> Seq("2"), "info" -> Seq("1"))

  "FieldMetaSet" should {
    "return a sequence of FieldMetadata" in {
      val fieldMetadataSet = FieldMetadataSet(ListMap( "2" -> fm2, "1" -> fm1))
      fieldMetadataSet.toSeq must_== Seq(fm2, fm1)
    }
    "return a FieldMetadata by id" in {
      val fieldMetadataSet = FieldMetadataSet(ListMap("1" -> fm1))
      fieldMetadataSet.get("1") must beSome(fm1)
    }
    "return a sequence of FieldMetadata grouped by category" in {
      val fieldMetadataSet = FieldMetadataSet(ListMap("2" -> fm2, "1" -> fm1))
      fieldMetadataSet.grouped must_== Seq((None, Seq(fm2)), (Some("info"), Seq(fm1)))
    }
    "serialize to JSON" in {
      val fieldMetadataSet = FieldMetadataSet(ListMap("2" -> fm2, "1" -> fm1))
      val json = play.api.libs.json.Json.toJson(fieldMetadataSet)
      json.toString must_== """[{"entityType":"RepositoryDescription","id":"2","name":"test2","description":"description","seeAlso":["seeAlso"]},{"entityType":"RepositoryDescription","id":"1","name":"test1","description":"description","category":"info","seeAlso":["seeAlso"]}]"""
    }
    "deserialize from JSON" in {
      val json = play.api.libs.json.Json.parse("""[{"entityType":"RepositoryDescription","id":"2","name":"test2","description":"description","seeAlso":["seeAlso"]},{"entityType":"RepositoryDescription","id":"1","name":"test1","description":"description","category":"info","seeAlso":["seeAlso"]}]""")
      val fieldMetadataSet = json.as[FieldMetadataSet]
      fieldMetadataSet.toSeq must_== Seq(fm2, fm1)
      fieldMetadataSet.grouped must_== Seq((None, Seq(fm2)), (Some("info"), Seq(fm1)))
    }
  }
}
