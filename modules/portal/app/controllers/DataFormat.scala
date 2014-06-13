package controllers

import defines.BindableEnum

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
object DataFormat extends BindableEnum {
  val Text = Value("txt")
  val Json = Value("json")
  val Xml = Value("xml")
  val Html = Value("html")
  val Csv = Value("csv")
}
