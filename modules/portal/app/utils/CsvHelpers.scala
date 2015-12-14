package utils

import java.io.StringWriter

import com.opencsv.CSVWriter

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait CsvHelpers {
  def writeCsv(headers: Seq[String], data: Seq[Array[String]], quote: Boolean = true): String = {
    val buffer = new StringWriter()
    val csvWriter = if (quote) new CSVWriter(buffer)
      else new CSVWriter(buffer, ',', CSVWriter.NO_QUOTE_CHARACTER)
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
