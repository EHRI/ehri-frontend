package controllers

import play.api.mvc.QueryStringBindable


object DataFormat extends Enumeration {
  val Text = Value("txt")
  val Json = Value("json")
  val Xml = Value("xml")
  val Html = Value("html")
  val Csv = Value("csv")
  val Tsv = Value("tsv")

  implicit val _binder: QueryStringBindable[DataFormat.Value] =
    utils.binders.queryStringBinder(this)
}
