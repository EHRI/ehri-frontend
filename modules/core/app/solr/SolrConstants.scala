package solr

/**
 * User: michaelb
 *
 * Various Solr-related constant values.
 */
object SolrConstants {

  /**
   * ID field
   */
  final val ID = "id"

  /**
    * Field that holds a documents accessors.
    */
  final val ACCESSOR_FIELD = "accessibleTo"

  /**
   * Placeholder value for when a document is
   * accessible to everybody. This is necessary because
   * there is apparently no way to filter docs where
   * a given multivalue field is empty.
   */
  final val ACCESSOR_ALL_PLACEHOLDER = "ALLUSERS"

  /**
   * The canonical "item name"
   */
  final val NAME_EXACT = "name"

  /**
   * Other forms of name
   */
  final val OTHER_NAMES = "otherFormsOfName"

  /**
   * Parallel forms of name
   */
  final val PARALLEL_NAMES = "parallelFormsOfName"

  /**
   * Name field for word matches
   */
  final val NAME_MATCH = "title" // FIXME???

  /**
   * Name field used for sorting.
   */
  final val NAME_SORT = "name_sort"

  /**
   * Name field used for ngram matching.
   */
  final val NAME_NGRAM = "name_ngram"

  /**
   * The field referring to the "item"'s id, rather than
   * that of the description.
   */
  final val ITEM_ID = "itemId"

  /**
   * The type-of-item key
   */
  final val TYPE = "type"

  /**
   * Field that contains everything else as concatenated text.
   */
  final val TEXT = "text"

  /**
   * Field containing the last-modified date
   */
  final val LAST_MODIFIED = "lastUpdated"

  /**
   * Default limit, if none is set
   */
  final val DEFAULT_SEARCH_LIMIT = 20

  /**
   * Default filter limit.
   */
  final val DEFAULT_FILTER_LIMIT = 100


}
