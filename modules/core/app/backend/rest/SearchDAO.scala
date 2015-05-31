package backend.rest

import javax.inject.Inject

import play.api.cache.CacheApi
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.json.{Reads, Json}
import backend.{Readable, ApiUser}
import utils.search.{SearchItemResolver, SearchHit}


trait SearchDAO extends RestDAO {

  def requestUrl = s"http://$host:$port/$mount/entities"

  def getAny[MT](id: String)(implicit apiUser: ApiUser,  rd: Readable[MT]): Future[MT] = {
    val url: String = enc(requestUrl, id)
    BackendRequest(url).withHeaders(authHeaders.toSeq: _*).get().map { response =>
      checkErrorAndParse(response, context = Some(url))(rd.restReads)
    }
  }

  def listByGid[MT](ids: Seq[Long])(implicit apiUser: ApiUser,  rd: Readable[MT]): Future[Seq[MT]] = {
    // NB: Using POST here because the list of IDs can
    // potentially overflow the GET param length...
    if (ids.isEmpty) Future.successful(Seq.empty[MT])
    else BackendRequest(enc(requestUrl, "listByGraphId"))
        .withHeaders(authHeaders.toSeq: _*).post(Json.toJson(ids)).map { response =>
      checkErrorAndParse(response)(Reads.seq(rd.restReads))
    }
  }

  def list[MT](ids: Seq[String])(implicit apiUser: ApiUser,  rd: Readable[MT]): Future[Seq[MT]] = {
    // NB: Using POST here because the list of IDs can
    // potentially overflow the GET param length...
    if (ids.isEmpty) Future.successful(Seq.empty[MT])
    else BackendRequest(requestUrl)
      .withHeaders(authHeaders.toSeq: _*).post(Json.toJson(ids)).map { response =>
      checkErrorAndParse(response, context = Some(requestUrl))(Reads.seq(rd.restReads))
    }
  }
}

case class Search @Inject() (implicit cache: CacheApi, app: play.api.Application) extends SearchDAO

/**
 * Resolve search hits to DB items by the GID field
 */
case class GidSearchResolver @Inject ()(implicit cache: CacheApi, app: play.api.Application) extends RestDAO with SearchDAO with SearchItemResolver {
  def resolve[MT](docs: Seq[SearchHit])(implicit apiUser: ApiUser,  rd: Readable[MT]): Future[Seq[MT]] =
    listByGid(docs.map(_.gid))
}

/**
 * Resolve search hits to DB items by the itemId field
 */
case class IdSearchResolver @Inject ()(implicit cache: CacheApi, app: play.api.Application) extends RestDAO with SearchDAO with SearchItemResolver {
  def resolve[MT](docs: Seq[SearchHit])(implicit apiUser: ApiUser,  rd: Readable[MT]): Future[Seq[MT]] =
    list(docs.map(_.itemId))
}
