package utils

import java.io.StringWriter

import com.opencsv.CSVWriter

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait CsvHelpers {
  def writeCsv(headers: Seq[String], data: Seq[Array[String]], sep: Char = ',', quote: Boolean = true): String = {
    val buffer = new StringWriter()
    val csvWriter = if (quote) new CSVWriter(buffer, sep)
      else new CSVWriter(buffer, sep, CSVWriter.NO_QUOTE_CHARACTER)
    try {
      csvWriter.writeNext(headers.toArray)
      for (item <- data) {
        csvWriter.writeNext(item)
      }
      buffer.getBuffer.toString
    } finally {
      csvWriter.close()
    }
  }
}

object CsvHelpers extends CsvHelpers
