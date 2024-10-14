package helpers

import models.EntityType
import play.api.libs.json.{JsValue, Json}

import java.nio.file.{Path, Paths}
import scala.io.{Codec, Source}

trait ResourceUtils {
  protected def readResource(v: EntityType.Value): JsValue = readResource(v.toString + ".json")

  protected def readResource(name: String): JsValue = Json.parse(resourceAsString(name))

  protected def resourceAsString(path: String): String = {
    val stream = getClass.getClassLoader.getResourceAsStream(path)
    if (stream == null) throw new RuntimeException(s"Resource $path not found")
    try {
      Source.fromInputStream(stream)(Codec.UTF8).mkString
    } finally {
      stream.close()
    }
  }

  protected def resourcePath(name: String): Path = Paths.get(getClass.getClassLoader.getResource(name).toURI)
}
