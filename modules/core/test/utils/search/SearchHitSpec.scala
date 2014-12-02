package utils.search

import play.api.test.PlaySpecification
import defines.EntityType

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
class SearchHitSpec extends PlaySpecification {

  private val testHit = SearchHit(
    id = "lu-006007-lu-11-iv-3-286-fra",
    itemId = "lu-006007-lu-11-iv-3-286",
    `type`= EntityType.DocumentaryUnit,
    gid = 87287L,
    fields = Map(
      "repositoryName" -> "Archives de la Ville de Luxembourg",
      "archivistNote_t" -> "Copied from online search engine",
      "name" -> "Demandes en obtention d' une autorisation de batir",
      "identifier" -> "LU 11 - IV/3 - 286",
      "scope" -> "high",
      "repositoryId" -> "lu-006007",
      "languageCode" -> "fra",
      "countryName" -> "Luxembourg",
      "publicationStatus" -> "Draft",
      "id" -> "lu-006007-lu-11-iv-3-286-fra",
      "countryCode" -> "lu",
      "itemId" -> "lu-006007-lu-11-iv-3-286",
      "copyright_t" -> "no",
      "title" -> "Demandes en obtention d' une autorisation de batir",
      "type" -> "documentaryUnit",
      "name_fr" -> "Demandes en obtention d' une autorisation de batir"
    ),
    Map(
      "itemId" -> List("<em>lu-006007-lu-11-iv-3-286</em>"),
       "title" -> List("<em>Demandes</em>")
    ),
    List()
  )

  private val data =
    """
      |<div class="search-item" id="lu-006007-lu-11-iv-3-286">
      |   <h3 class="search-item-heading type-highlight documentaryUnit">
      |      <a href="/units/lu-006007-lu-11-iv-3-286">Demandes en obtention d&#x27; une autorisation de batir </a>
      |   </h3>
      |   <div class="search-item-body">
      |     <small>
      |       <ul class="list-unstyled text-muted list-inline inline-separator">
      |        <li><a class="alt" href="/institutions/lu-006007">Archives de la Ville de Luxembourg</a></li>
      |        <li><span class="text-muted">French</span></li>
      |          <time datetime="2013-09-18T11:50:35.153+01:00">Updated 7 months ago</time>
      |        </li>
      |       </ul>
      |     </small>
      |   </div>
      |</div>
    """.stripMargin

  "search highlighting" should {
    "highlight body fields but not programmatic ones" in {
      val hl = testHit.highlight(data)
      hl.body must contain("<em>Demandes</em>")
      hl.body must not contain("<em>lu-006007-lu-11-iv-3-286</em>")
    }
  }
}
