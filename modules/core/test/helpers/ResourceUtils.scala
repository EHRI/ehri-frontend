package helpers

import scala.io.Source

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait ResourceUtils {
  protected def resourceAsString(name: String): String = Source.fromInputStream(getClass
    .getClassLoader.getResourceAsStream(name))
    .getLines().mkString("\n")
}
