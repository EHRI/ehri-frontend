package backend.rest

import play.api.{Logger, Play}
import play.api.http.{Writeable, ContentTypeOf, HeaderNames, ContentTypes}
import play.api.libs.json._
import backend.{ErrorSet, ApiUser}
import com.fasterxml.jackson.core.JsonParseException
import utils.Page


trait RestDAO {

  import play.api.libs.concurrent.Execution.Implicits._
  import play.api.libs.ws.{EmptyBody, InMemoryBody, WSBody, WS, WSResponse}

  /**
   * Wrapper for WS.
   */
  private[rest] case class BackendRequest(
    url: String,
    headers: Seq[(String,String)] = Seq.empty,
    queryString: Seq[(String,String)] = Seq.empty,
    method: String = "GET",
    body: WSBody = EmptyBody
    )(implicit apiUser: ApiUser, app: play.api.Application) {

    import scala.util.matching.Regex
    import play.api.cache.Cache
    import scala.concurrent.Future

    private val CCExtractor: Regex = """.*?max-age=(\d+)""".r

    private def conditionalCache(url: String, method: String, response: WSResponse): WSResponse = {
      val doCache = app.configuration.getBoolean("ehri.ws.cache").getOrElse(false)
      if (doCache) {
        response.header(HeaderNames.CACHE_CONTROL) match {
          // if there's a max-age cache on a GET request cache the response.
          case Some(CCExtractor(age)) if method == "GET"
            && age.toInt > 0
            && response.status >= 200 && response.status < 300 =>
            Logger.trace(s"CACHING: $method $url $age")
            Cache.set(url, response, age.toInt)
          // if not, ensure it's invalidated
          case _ if method != "GET" =>
            val itemUrl = response.header(HeaderNames.LOCATION).getOrElse(url)
            Logger.trace(s"Evicting from cache: $method $itemUrl")
            Cache.remove(itemUrl)
          case _ =>
        }
      }
      response
    }

    private def queryStringMap: Map[String,Seq[String]] =
      queryString.foldLeft(Map.empty[String,Seq[String]]) { case (m, (k, v)) =>
        m.get(k).map { s =>
          m.updated(k, s ++ Seq(v))
        } getOrElse {
          m.updated(k, Seq(v))
        }
      }

    private def fullUrl: String = s"$url?${joinQueryString(queryStringMap)}"

    private def runWs: Future[WSResponse] = {
      Logger.debug(s"WS: $apiUser $method $fullUrl")
      WS.url(url)
        .withQueryString(queryString: _*)
        .withHeaders(headers: _*)
        .withBody(body)
        .execute(method)
        .map(checkError)
        .map(r => conditionalCache(url, method, r))
    }

    def get(): Future[WSResponse] = copy(method = "GET").execute()

    def post[T](body: T)(implicit wrt: Writeable[T], ct: ContentTypeOf[T]) =
      withMethod("POST").withBody(body).execute()

    def put[T](body: T)(implicit wrt: Writeable[T], ct: ContentTypeOf[T]) =
      withMethod("PUT").withBody(body).execute()

    def delete() = withMethod("DELETE").execute()

    /**
     * Sets the body for this request. Copy and paste from WSRequest :(
     */
    def withBody[T](body: T)(implicit wrt: Writeable[T], ct: ContentTypeOf[T]): BackendRequest = {
      val wsBody = InMemoryBody(wrt.transform(body))
      if (headers.contains("Content-Type")) {
        withBody(wsBody)
      } else {
        ct.mimeType.fold(withBody(wsBody)) { contentType =>
          withBody(wsBody).withHeaders("Content-Type" -> contentType)
        }
      }
    }

    def withBody(body: WSBody):BackendRequest = copy(body = body)

    def withHeaders(hrds: (String, String)*): BackendRequest =
      copy(headers = headers ++ hrds)

    def withQueryString(parameters: (String, String)*): BackendRequest =
      copy(queryString = queryString ++ parameters)

    def withMethod(method: String) = copy(method = method)

    def execute(): Future[WSResponse] = {
      if (method == "GET") Cache.getAs[WSResponse](url) match {
        case Some(r) =>
          Logger.trace(s"Retrieved from cache: $url")
          Future.successful(r)
        case _ => runWs
      } else runWs
    }
  }

  import Constants._
  import play.api.http.Status._
  import play.api.Play.current

  /**
   * Header to add for log messages.
   */
  def msgHeader(msg: Option[String]): Seq[(String,String)] =
    msg.map(m => Seq(LOG_MESSAGE_HEADER_NAME -> m)).getOrElse(Seq.empty)

  /**
   * Join params into a query string
   */
  def joinQueryString(qs: Map[String, Seq[String]]): String = {
    import java.net.URLEncoder
    qs.map { case (key, vals) => {
      vals.map(v => "%s=%s".format(key, URLEncoder.encode(v, "UTF-8")))
    }}.flatten.mkString("&")
  }

  /**
   * Standard headers we sent to every Neo4j/EHRI Server request.
   */
  val headers = Map(
    HeaderNames.ACCEPT -> ContentTypes.JSON,
    HeaderNames.CONTENT_TYPE -> ContentTypes.JSON
  )

  /**
   * Headers to add to outgoing request...
   * @return
   */
  def authHeaders(implicit apiUser: ApiUser): Map[String, String] = apiUser.id match {
    case Some(id) => headers + (AUTH_HEADER_NAME -> id)
    case None => headers
  }

  /**
   * Create a web request with correct auth parameters for the REST API.
   */
  import scala.collection.JavaConversions._
  private lazy val includeProps
    = Play.current.configuration.getStringList("ehri.backend.includedProperties").map(_.toList)
        .getOrElse(List.empty[String])


  def userCall(url: String, params: Seq[(String,String)] = Seq.empty)(implicit apiUser: ApiUser): BackendRequest = {
    BackendRequest(url).withHeaders(authHeaders.toSeq: _*)
      .withQueryString(params: _*)
      .withQueryString(includeProps.map(p => Constants.INCLUDE_PROPERTIES_PARAM -> p).toSeq: _*)
  }

  /**
   * Encode a bunch of URL parts.
   */
  def enc(s: Any*) = {
    import java.net.URI
    val url = new java.net.URL(s.mkString("/"))
    val uri: URI = new URI(url.getProtocol, url.getUserInfo, url.getHost, url.getPort, url.getPath, url.getQuery, url.getRef)
    uri.toString
  }

  lazy val host: String = Play.current.configuration.getString("neo4j.server.host").get
  lazy val port: Int = Play.current.configuration.getInt("neo4j.server.port").get
  lazy val mount: String = Play.current.configuration.getString("neo4j.server.endpoint").get

  protected def checkError(response: WSResponse): WSResponse = {
    Logger.logger.trace("Response body ! : {}", response.body)
    response.status match {
      case OK | CREATED => response
      case e => e match {

        case UNAUTHORIZED => {
          response.json.validate[PermissionDenied].fold(
            err => throw PermissionDenied(),
            perm => {
              Logger.logger.error("Permission denied error! : {}", response.json)
              throw perm
            }
          )
        }
        case BAD_REQUEST => try {
          response.json.validate[ErrorSet].fold(
            e => {
              // Temporary approach to handling random Deserialization errors.
              // In practice this should happen
              if ((response.json \ "error").asOpt[String] == Some("DeserializationError")) {
                Logger.logger.error("Derialization error! : {}", response.json)
                throw DeserializationError()
              } else {
                Logger.error("Bad request: " + response.body)
                throw sys.error(s"Unexpected BAD REQUEST: $e \n${response.body}")
              }
            },
            errorSet => {
              Logger.logger.warn("ValidationError ! : {}", response.json)
              throw ValidationError(errorSet)
            }
          )
        } catch {
          case e: JsonParseException => {
            Logger.error(response.body)
            throw new BadRequest(response.body)
          }
        }
        case NOT_FOUND => {
          //Logger.logger.error("404: {} -> {}", Array(response.underlying[AHCRe].getUri, response.body))
          response.json.validate[ItemNotFound].fold(
            e => throw new ItemNotFound(),
            err => throw err
          )
        }
        case _ => {
          val err = s"Unexpected response: ${response.status}: '${response.body}'"
          Logger.logger.error(err)
          sys.error(err)
        }
      }
    }
  }

  private[rest] def checkErrorAndParse[T](response: WSResponse)(implicit reader: Reads[T]): T =
    jsonReadToRestError(checkError(response).json, reader, context = None)

  private[rest] def checkErrorAndParse[T](response: WSResponse, context: Option[String])(implicit reader: Reads[T]): T =
    jsonReadToRestError(checkError(response).json, reader, context)

  private[rest] def parsePage[T](response: WSResponse)(implicit rd: Reads[T]): Page[T] =
    parsePage(response, None)(rd)

  private[rest] def parsePage[T](response: WSResponse, context: Option[String])(implicit rd: Reads[T]): Page[T] = {
    checkError(response).json.validate(Reads.seq(rd)).fold(
      invalid => {
        println(Json.prettyPrint(response.json))
        Logger.error("Bad JSON: " + invalid)
        throw new BadJson(invalid, url = context, data = Some(Json.prettyPrint(response.json)))
      },
      items => {
        val Extractor = """page=(-?\d+); count=(-?\d+); total=(-?\d+)""".r
        val pagination = response.header(HeaderNames.CONTENT_RANGE).getOrElse("")
        Extractor.findFirstIn(pagination) match {
          case Some(Extractor(page, count, total)) => Page(
            items = items,
            page = page.toInt,
            count = count.toInt,
            total = total.toInt
          )
          case m => Page(items = items, page = 1, count = -1, total = -1)
        }
      }
    )
  }

  private[rest] def jsonReadToRestError[T](json: JsValue, reader: Reads[T], context: Option[String] = None): T = {
    json.validate(reader).fold(
      invalid => {
        println(Json.prettyPrint(json))
        Logger.error("Bad JSON: " + invalid)
        throw new BadJson(invalid, url = context, data = Some(Json.prettyPrint(json)))
      },
      valid => valid
    )
  }
}