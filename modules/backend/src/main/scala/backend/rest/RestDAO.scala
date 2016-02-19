package backend.rest

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

import play.api.Logger
import play.api.http.{Writeable, ContentTypeOf, HeaderNames}
import play.api.libs.json._
import backend._
import com.fasterxml.jackson.core.JsonParseException
import play.api.libs.ws._
import utils.{RangePage, RangeParams, Page}
import scala.concurrent.Future
import scala.concurrent.duration.Duration

import play.api.cache.CacheApi

trait RestDAO {

  implicit def config: play.api.Configuration
  implicit def cache: CacheApi
  def ws: WSClient

  import play.api.libs.concurrent.Execution.Implicits._
  import play.api.libs.ws.{EmptyBody, InMemoryBody, WSBody, WSResponse}

  private def logger: Logger = Logger(this.getClass)

  /**
   * Wrapper for WS.
   */
  private[rest] case class BackendRequest(
    url: String,
    headers: Seq[(String,String)] = Seq.empty,
    queryString: Seq[(String,String)] = Seq.empty,
    method: String = "GET",
    body: WSBody = EmptyBody
    )(implicit apiUser: ApiUser) {

    import scala.util.matching.Regex
    import scala.concurrent.Future

    private val CCExtractor: Regex = """.*?max-age=(\d+)""".r

    lazy val credentials = for {
      username <- config.getString("services.ehridata.username")
      password <- config.getString("services.ehridata.password")
    } yield (username, password)

    private def conditionalCache(url: String, method: String, response: WSResponse): WSResponse = {
      val doCache = config.getBoolean("ehri.ws.cache").getOrElse(false)
      if (doCache) {
        response.header(HeaderNames.CACHE_CONTROL) match {
          // if there's a max-age cache on a GET request cache the response.
          case Some(CCExtractor(age)) if method == "GET"
            && age.toInt > 0
            && response.status >= 200 && response.status < 300 =>
            logger.trace(s"CACHING: $method $url $age")
            cache.set(url, response, Duration(age.toInt, TimeUnit.SECONDS))
          // if not, ensure it's invalidated
          case _ if method != "GET" =>
            val itemUrl = response.header(HeaderNames.LOCATION).getOrElse(url)
            logger.trace(s"Evicting from cache: $method $itemUrl")
            cache.remove(itemUrl)
          case _ =>
        }
      }
      response
    }

    private def queryStringMap: Map[String,Seq[String]] =
      queryString.foldLeft(Map.empty[String,Seq[String]]) { case (m, (k, v)) =>
        m.updated(k, v +: m.getOrElse(k, Seq.empty))
      }

    private def fullUrl: String =
      if (queryStringMap.nonEmpty) s"$url?${joinQueryString(queryStringMap)}" else url

    private def holderWithAuth: WSRequest = {
      val holder = ws.url(url)
        .withQueryString(queryString: _*)
        .withHeaders(headers: _*)
        .withBody(body)
      credentials.fold(holder) { case (un, pw) =>
        holder.withAuth(un, pw, WSAuthScheme.BASIC)
      }
    }

    private def runWs: Future[WSResponse] = {
      logger.debug(s"WS: $apiUser $method $fullUrl")
      holderWithAuth
        .execute(method)
        .map(checkError)
        .map(r => conditionalCache(url, method, r))
    }

    def stream(): Future[StreamedResponse] = {
      logger.debug(s"WS (stream): $apiUser $method $fullUrl")
      holderWithAuth.stream()
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
      if (headers.toMap.contains(HeaderNames.CONTENT_TYPE)) {
        withBody(wsBody)
      } else {
        ct.mimeType.fold(withBody(wsBody)) { contentType =>
          withBody(wsBody).withHeaders(HeaderNames.CONTENT_TYPE -> contentType)
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
      if (method == "GET") cache.get[WSResponse](url) match {
        case Some(r) =>
          logger.trace(s"Retrieved from cache: $url")
          Future.successful(r)
        case _ => runWs
      } else runWs
    }
  }

  import backend.rest.Constants._
  import play.api.http.Status._

  /**
   * Header to add for log messages.
   */
  protected def msgHeader(msg: Option[String]): Seq[(String,String)] =
    msg
      .map(m => Seq(LOG_MESSAGE_HEADER_NAME -> URLEncoder.encode(m, StandardCharsets.UTF_8.name)))
      .getOrElse(Seq.empty)

  /**
   * Join params into a query string
   */
  protected def joinQueryString(qs: Map[String, Seq[String]]): String =
    qs.flatMap { case (key, vals) =>
      vals.map(v => s"$key=${URLEncoder.encode(v, StandardCharsets.UTF_8.name)}")
    }.mkString("&")

  /**
   * Standard headers we sent to every Neo4j/EHRI Server request.
   */
  protected val headers = Map(
    HeaderNames.ACCEPT_CHARSET -> StandardCharsets.UTF_8.name
  )

  /**
   * Headers to add to outgoing request...
   * @return
   */
  private[rest] def authHeaders(implicit apiUser: ApiUser): Map[String, String] = apiUser match {
    case AuthenticatedUser(id) => headers + (AUTH_HEADER_NAME -> id)
    case AnonymousUser => headers
  }

  /**
   * Create a web request with correct auth parameters for the REST API.
   */
  import scala.collection.JavaConversions._
  private lazy val includeProps =
    config.getStringList("ehri.backend.includedProperties").map(_.toSeq)
        .getOrElse(Seq.empty[String])


  protected def userCall(url: String, params: Seq[(String,String)] = Seq.empty)(implicit apiUser: ApiUser): BackendRequest = {
    BackendRequest(url)
      .withHeaders(authHeaders.toSeq: _*)
      .withQueryString(params: _*)
      .withQueryString(includeProps.map(p => Constants.INCLUDE_PROPERTIES_PARAM -> p): _*)
  }

  /**
   * Fetch a range, working out if there are more items by going one-beyond-the-end.
   */
  protected def fetchRange[T](req: BackendRequest, params: RangeParams, context: Option[String])(
      implicit reader: Reads[T]): Future[RangePage[T]] = {
    val incParams = if(params.hasLimit) params.copy(limit = params.limit + 1) else params
    req.withHeaders(STREAM_HEADER_NAME -> true.toString)
      .withQueryString(incParams.queryParams: _*)
        .get().map { r =>
      val page = parsePage(r, context)(reader)
      val more = if (params.hasLimit) page.size > params.limit else false
      val pageItems = if (params.hasLimit) page.items.take(params.limit) else page.items
      RangePage(params.offset, params.limit, more = more, items = pageItems)
    }
  }

  /**
   * Encode a bunch of URL parts.
   */
  protected def enc(s: Any*) = {
    import java.net.URI
    val url = new java.net.URL(s.mkString("/"))
    val uri: URI = new URI(url.getProtocol, url.getUserInfo, url.getHost, url.getPort, url.getPath, url.getQuery, url.getRef)
    uri.toString
  }

  protected def baseUrl: String = utils.serviceBaseUrl("ehridata", config)

  protected def canonicalUrl[MT: Resource](id: String): String =
    enc(baseUrl, Resource[MT].entityType, id)

  protected def checkError(response: WSResponse): WSResponse = {
    logger.trace(s"Response body ! : ${response.body}")
    response.status match {
      case OK | CREATED => response
      case e => e match {

        case UNAUTHORIZED =>
          response.json.validate[PermissionDenied].fold(
            err => throw PermissionDenied(),
            perm => {
              logger.error(s"Permission denied error! : ${response.json}")
              throw perm
            }
          )
        case BAD_REQUEST => try {
          response.json.validate[ErrorSet].fold(
            e => {
              // Temporary approach to handling random Deserialization errors.
              // In practice this should happen
              if ((response.json \ "error").asOpt[String].contains("DeserializationError")) {
                logger.error(s"Derialization error! : ${response.json}")
                throw DeserializationError()
              } else {
                logger.error("Bad request: " + response.body)
                throw sys.error(s"Unexpected BAD REQUEST: $e \n${response.body}")
              }
            },
            errorSet => {
              logger.warn(s"ValidationError ! : ${response.json}")
              throw ValidationError(errorSet)
            }
          )
        } catch {
          case e: JsonParseException =>
            throw new BadRequest(response.body)
        }
        case NOT_FOUND =>
          //logger.error("404: {} -> {}", Array(response.underlying[AHCRe].getUri, response.body))
          response.json.validate[ItemNotFound].fold(
            e => throw new ItemNotFound(),
            err => throw err
          )
        case _ =>
          val err = s"Unexpected response: ${response.status}: '${response.body}'"
          logger.error(err)
          sys.error(err)
      }
    }
  }

  private[rest] def checkErrorAndParse[T](response: WSResponse)(implicit reader: Reads[T]): T =
    jsonReadToRestError(checkError(response).json, reader, context = None)

  private[rest] def checkErrorAndParse[T](response: WSResponse, context: Option[String])(implicit reader: Reads[T]): T =
    jsonReadToRestError(checkError(response).json, reader, context)

  private[rest] def parsePage[T](response: WSResponse)(implicit rd: Reads[T]): Page[T] =
    parsePage(response, None)(rd)

  /**
   * List header parser
   */
  private[rest] val Extractor = """offset=(-?\d+); limit=(-?\d+); total=(-?\d+)""".r

  private[rest] def parsePagination(response: WSResponse, context: Option[String]): Option[(Int, Int, Int)] = {
    val pagination = response.header(HeaderNames.CONTENT_RANGE).getOrElse("")
    Extractor.findFirstIn(pagination) match {
      case Some(Extractor(offset, limit, total)) => Some((offset.toInt, limit.toInt, total.toInt))
      case _ => None
    }
  }

  private[rest] def parsePage[T](response: WSResponse, context: Option[String])(implicit rd: Reads[T]): Page[T] = {
    checkError(response).json.validate(Reads.seq(rd)).fold(
      invalid => throw new BadJson(
        invalid, url = context, data = Some(Json.prettyPrint(response.json))),
      items => parsePagination(response, context) match {
        case Some((offset, limit, total)) => Page(
          items = items,
          offset = offset,
          limit = limit,
          total = total
        )
        case _ => Page(
          items = items,
          offset = 0,
          limit = Constants.DEFAULT_LIST_LIMIT,
          total = -1
        )
      }
    )
  }

  private[rest] def jsonReadToRestError[T](json: JsValue, reader: Reads[T], context: Option[String] = None): T = {
    json.validate(reader).fold(
      invalid => throw new BadJson(
        invalid, url = context, data = Some(Json.prettyPrint(json))),
      valid => valid
    )
  }
}