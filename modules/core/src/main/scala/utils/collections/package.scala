package utils

package object collections {
  /**
    * This implicit class provides a groupByOrdered method that groups elements of an Iterable by a key function
    * and returns a Seq of pairs of the key and the Iterable of elements that have that key.
    * The keys are ordered in the order they first appear in the Iterable.
    *
    * @param t The Iterable to group
    * @tparam A The type of the elements in the Iterable
    */
  implicit class GroupByOrderedImplicitImpl[A](val t: Iterable[A]) extends AnyVal {
    def groupByOrdered[K](f: A => K): Seq[(K, Iterable[A])] = {
      val keys = t.map(f).toSeq.distinct
      val groups = t.groupBy(f)
      keys.map(key => key -> groups(key))
    }
  }
}
