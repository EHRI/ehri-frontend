package controllers

import defines.BindableEnum

object DataFormat extends BindableEnum {
  val Text = Value("txt")
  val Json = Value("json")
  val Xml = Value("xml")
  val Html = Value("html")
  val Csv = Value("csv")
  val Tsv = Value("tsv")
}
