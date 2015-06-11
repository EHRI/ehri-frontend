package defines

/**
 * The implicit values in this package allow the Play routes
 * to bind/unbind enumeration values, without those enums having
 * to be specifically aware of Play functionality.
 *
 * These values are imported into the generated routes files by
 * the build.
 */
package object binders {
  implicit val entityTypeBinder = EnumerationBinders.bindableEnum(EntityType)
  implicit val entityTypeQueryBinder = EnumerationBinders.queryStringBinder(EntityType)
}
