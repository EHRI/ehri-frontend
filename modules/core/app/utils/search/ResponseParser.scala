package utils.search

import com.github.seratch.scalikesolr.WriterType

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait ResponseParser {
  def apply(response: String): QueryResponse
  def writerType: WriterType
}
