package controllers.base

import java.io.StringWriter

import com.opencsv.CSVWriter

trait CsvHelpers {
  def writeCsv(headers: Seq[String], data: Seq[Array[String]]): String = {
    val buffer = new StringWriter()
    val csvWriter = new CSVWriter(buffer)
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
