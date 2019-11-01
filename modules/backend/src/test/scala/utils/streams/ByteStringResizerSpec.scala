package utils.streams

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import play.api.test.PlaySpecification

import scala.concurrent.Await
import scala.concurrent.duration._

class ByteStringResizerSpec extends PlaySpecification {

  private implicit val as = ActorSystem.create("testing")
  private implicit val mat = Materializer(as)

  def count(size: Int, bytes: ByteString*): Int = Await.result(
    Source.apply(bytes.toList)
      .via(ByteStreamResizer.resize(size)).runFold(0){ case (i,_) => i + 1}, 5.seconds)

  "resizer" should {

    "handle smaller elements" in {
      val bytes = ByteString.fromString("abcd")
      count(1, bytes) must_== bytes.length
    }

    "handle larger elements" in {
      val bytes = "abcdefghij".grouped(1).map(ByteString.fromString).toVector
      count(4, bytes: _*) must_== 3
    }

    "handle single smaller elements" in {
      val bytes = ByteString.fromString("abcd")
      count(100, bytes) must_== 1
    }

    "handle smaller elements in a larger buffer" in {
      val bytes = "abcdefghij".grouped(1).map(ByteString.fromString).toVector
      count(20, bytes: _*) must_== 1
    }


    "handle large byte streams" in {
      val data = StreamConverters.fromInputStream(() =>
        getClass.getClassLoader.getResourceAsStream("cypher-json.json"))

      def countBytes(s: Source[ByteString, _]): Int =
        Await.result(s.runFold(0){case (i, b) => i + b.length}, 5.seconds)

      val n1 = countBytes(data.via(ByteStreamResizer.resize(8)))
      val n2 = countBytes(data)
      n1 must_== n2
    }
  }
}
