import scala.io.Source

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
package object helpers {
  /**
   * Load a resource as a string
   */
  def resourceAsString(name: String): String = Source.fromInputStream(getClass
    .getClassLoader.getResourceAsStream(name))
    .getLines().mkString("\n")
}
