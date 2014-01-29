package object utils {

  import play.api.mvc.RequestHeader

  def isAjax(implicit request: RequestHeader): Boolean =
    request.headers.get("X-REQUESTED-WITH") .map(_.toUpperCase == "XMLHTTPREQUEST").getOrElse(false)

  def joinPath(path: String, qs: Map[String, Seq[String]]): String = {
    List(path, joinQueryString(qs)).filterNot(_=="").mkString("?")
  }

  def joinQueryString(qs: Map[String, Seq[String]]): String = {
    import java.net.URLEncoder
    qs.map { case (key, vals) => {
      vals.map(v => "%s=%s".format(key, URLEncoder.encode(v, "UTF-8")))    
    }}.flatten.mkString("&")
  }
}