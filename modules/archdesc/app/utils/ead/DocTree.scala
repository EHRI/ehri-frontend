package utils.ead

import models.DocumentaryUnit

/**
 * A util class for representing a hierarchy of
 * documentary units.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class DocTree(
  item: DocumentaryUnit,
  children: Seq[DocTree]
)
