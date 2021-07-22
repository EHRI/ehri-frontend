package services.transformation

import akka.NotUsed
import akka.http.scaladsl.model.ContentType
import akka.stream.alpakka.text.scaladsl.TextFlow
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import models.DataTransformation
import play.api.libs.Codecs
import play.api.libs.json.{JsObject, Json}

import java.nio.charset.StandardCharsets

package object utils {

  /**
    * Given a set of transformations calculate a unique tag/digest for this
    * operation.
    *
    * @param mappings a set of transform operations
    * @return an md5 string representing the transformations
    */
  def digest(mappings: Seq[(DataTransformation.TransformationType.Value, String, JsObject)]): String = {
    val sb = new StringBuilder
    mappings.foreach { case (mapType, map, params) =>
      sb.append(mapType)
      sb.append(map)
      sb.append(Json.stringify(params))
    }
    Codecs.md5(sb.toString())
  }

  /**
    * Given a string of input source and a set of transformations, calculate
    * a unique tag/digest for this operation.
    *
    * @param src      the input data, or a hash thereof
    * @param mappings a set of transform operations
    * @return an md5 string representing the overall operation
    */
  def digest(src: String, mappings: Seq[(DataTransformation.TransformationType.Value, String, JsObject)]): String =
    Codecs.md5(src + digest(mappings))

  /**
    * Given a file content type get a transcoder to convert it to UTF-8
    * for further processing. This will only return a value of the content type
    * has a charset argument and it's not already UTF-8.
    *
    * @param contentType an optional content type
    * @return an optional transcoding flow
    */
  def getUtf8Transcoder(contentType: Option[String]): Option[Flow[ByteString, ByteString, NotUsed]] = {
    // If the file has a charset and it's not UTF-8 we need to transcode it
    // Otherwise we have to assume it's UTF-8 already, which should be the default.
    contentType.flatMap { ct =>
      ContentType.parse(ct) match {
        case Left(_) => None
        case Right(contentType) =>
          contentType.charsetOption.map(_.nioCharset()).filter(_ != StandardCharsets.UTF_8).map { charset =>
            TextFlow.transcoding(charset, StandardCharsets.UTF_8)
          }
      }
    }
  }
}
