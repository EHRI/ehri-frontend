package solr

/**
 * User: michaelb
 *
 * Various Solr-related constant values.
 */
object SolrConstants {

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



}
