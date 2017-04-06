package utils.streams

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink, Source, StreamConverters}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.util.ByteString
import org.apache.commons.io.IOUtils
import play.api.libs.json._
import play.api.test.PlaySpecification
import utils.streams.JsonStream.{JsEndObject, JsEvent, JsStartArray}

import scala.concurrent.Await
import scala.concurrent.duration._


class JsonStreamSpec extends PlaySpecification {

  private implicit val as = ActorSystem.create("testing")
  private implicit val mat = ActorMaterializer.create(as)


  private val objectBytes: Array[Byte] =
    """{
      |  "a": [1, 2],
      |  "b": {
      |    "f": -3,
      |    "g": 1.0,
      |    "h": "Hello, \"world\"",
      |    "i": null,
      |    "j": true,
      |    "k": [true, false, {"foo": "bar", "bar": ["baz"]}]
      |  },
      |  "c": 1.0,
      |  "d": 2,
      |  "e": "foo\\\"bar\"",
      |  "l": {"foo": "bar"},
      |  "m": "\u00f8",
      |  "\"n\"": 0e-005
      |}
    """.stripMargin.getBytes

  private val arrayBytes: Array[Byte] =
    """[
      |  "a",
      |  {
      |    "f": 3,
      |    "g": 1.0,
      |    "h": "Hello, world",
      |    "i": null,
      |    "j": true,
      |    "k": [true, false, {"foo": "bar", "bar": ["baz"]}]
      |  },
      |  [1, 2],
      |  null,
      |  true,
      |  false
      |]
    """.stripMargin.getBytes


  private def items(bytes: Array[Byte], p: String): Seq[JsValue] = Await.result(Source
    .single(ByteString.fromArray(bytes))
    .via(JsonStream.items(p))
    .map(b => Json.parse(b.utf8String))
    .runFold(Seq.empty[JsValue])(_ :+ _), 5.seconds)

  "JsonStream" should {
    "tokenize correctly" in {
      import utils.streams.JsonStream.{JsEvent, JsStartObject, JsValueNumber}

      val src = Source.single(ByteString.fromArray(objectBytes))
      val events = Await.result(src.via(JsonStream.parse)
        .runFold(Seq.empty[(String, JsEvent)])(_ :+ _), 5.seconds)

      events.head must_== "" -> JsStartObject
      events(11) must_== "b.g" -> JsValueNumber(1.0)

      val src2 = Source.single(ByteString.fromArray(arrayBytes))
      val events2 = Await.result(src2.via(JsonStream.parse)
        .runFold(Seq.empty[(String, JsEvent)])(_ :+ _), 5.seconds)

      events2.head must_== "" -> JsStartArray
    }

    "tokenize big streams" in {
        val src = StreamConverters.fromInputStream(() =>
          getClass.getClassLoader
            .getResourceAsStream("cypher-json.json"))
        val count = Await.result(src.via(JsonStream.parse)
          .runFold(0) { case (n, _) => n + 1 }, 5.seconds)
        count must beGreaterThan(100)
      val last = Await.result(src.via(JsonStream.parse)
        .runWith(Sink.last), 5.seconds)
      last must_== ("" -> JsEndObject)
    }

    "emit at the correct time" in {
      val (pub, sub) = TestSource.probe[ByteString]
        .via(JsonStream.parse)
        .toMat(TestSink.probe[(String, JsEvent)])(Keep.both)
        .run()

      sub.request(4)
      pub.sendNext(ByteString.fromString("[\"foo\", \"bar\"]"))
      sub.expectNext(
        ("", JsStartArray),
        ("item", JsonStream.JsValueString("foo")),
        ("item", JsonStream.JsValueString("bar")),
        ("", JsonStream.JsEndArray)
      )
      pub.sendComplete()
      sub.expectComplete()
      success
    }

    "handle incomplete streams" in {
      val (pub, sub) = TestSource.probe[ByteString]
        .via(JsonStream.parse)
        .toMat(TestSink.probe[(String, JsEvent)])(Keep.both)
        .run()

      sub.request(1)
      pub.sendNext(ByteString.fromString("["))
      sub.expectNext() must_== ("" -> JsStartArray)

      sub.request(1)
      pub.sendNext(ByteString.fromString("\"start..."))
      sub.expectNoMsg()
      sub.request(1)
      pub.sendNext(ByteString.fromString("...end\""))
      sub.expectNext() must_== ("item" -> JsonStream.JsValueString("start......end"))

      val e = new IllegalArgumentException("oops")
      pub.sendError(e)
      sub.expectError() must_== e
    }
  }

  "Accumulator" should {
    "parse objects" in {
      items(objectBytes, "") must_== Seq(Json.parse(objectBytes))
      items(objectBytes, "a") must_== Seq(Json.arr(1, 2))
      items(objectBytes, "a.item") must_== Seq(JsNumber(1), JsNumber(2))
      items(objectBytes, "b.k.item") must_== Seq(JsBoolean(true),
          JsBoolean(false), Json.obj("foo" -> "bar", "bar" -> Json.arr("baz")))
      items(objectBytes, "b.i") must_== Seq(JsNull)
      items(objectBytes, "c") must_== Seq(JsNumber(1.0))
      items(objectBytes, "l.foo") must_== Seq(JsString("bar"))
      items(objectBytes, "m") must_== Seq(JsString("ø"))
    }

    "parse arrays" in {
      items(arrayBytes, "") must_== Seq(Json.parse(arrayBytes))
      items(arrayBytes, "item").head must_== JsString("a")
      items(arrayBytes, "item")(2) must_== Json.arr(1, 2)
      items(arrayBytes, "item").last must_== JsBoolean(false)
    }

    "parse big streams" in {
      val loader = () => getClass.getClassLoader.getResourceAsStream("cypher-json.json")
      val src = StreamConverters.fromInputStream(loader).via(JsonStream.items(""))
      val objViaStream = Json.parse(Await.result(src.runWith(Sink.head), 5.seconds).toArray)
      val objViaBytes = Json.parse(IOUtils.toByteArray(loader()))
      objViaStream must_== objViaBytes
    }
  }

  "Builder" should {
    "aggregate objects correctly" in {
      val json1 = Json.parse(objectBytes)
      val builder = new JsonBuilder()

      val src = Source.single(ByteString.fromArray(objectBytes))
      Await.result(src.via(JsonStream.parse).runForeach { case (p, ev) =>
        builder.event(ev)
      }, 5.seconds)

      json1 must_== Json.parse(builder.value.utf8String)
    }
  }
}
