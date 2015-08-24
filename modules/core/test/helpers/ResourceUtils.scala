package helpers

import scala.io.{Codec, Source}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait ResourceUtils {
  protected def resourceAsString(name: String): String = Source.fromInputStream(getClass
    .getClassLoader.getResourceAsStream(name))(Codec.UTF8)
    .getLines().mkString("\n")
}
