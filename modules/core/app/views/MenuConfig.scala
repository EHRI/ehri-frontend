package views

import java.util


/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
object MenuConfig {

  import collection.JavaConverters._

  // Ugh, cannot find a mutable sorted Scala collection, so using
  // this Java LinkedHashMap with a converter
  private val mainSection = new java.util.LinkedHashMap[String,String]()
  private val adminSection = new util.LinkedHashMap[String,String]()

  def putMain(key: String, url: String) = mainSection.put(key, url)
  def putAdmin(key: String, url: String) = adminSection.put(key, url)

  def main = mainSection.asScala
  def admin = adminSection.asScala
}
