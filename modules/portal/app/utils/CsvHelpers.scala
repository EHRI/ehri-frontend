package utils

import com.fasterxml.jackson.core.FormatSchema
import com.fasterxml.jackson.dataformat.csv.{CsvGenerator, CsvMapper, CsvSchema}

trait CsvHelpers {
  def writeCsv(headers: Seq[String], data: Seq[Array[String]], sep: Char = ','): String = {
    val format = CsvSchema.builder().setColumnSeparator(sep).setUseHeader(true)
    val schema: FormatSchema = headers.foldLeft(format) { (s, h) =>
      s.addColumn(h)
    }.build()

    CsvHelpers.mapper.writer(schema).writeValueAsString(data.toArray)
  }
}

object CsvHelpers extends CsvHelpers {
  // String quoting check necessary to avoid over-cautious quoting
  // of unicode-containing values
  val mapper = new CsvMapper()
    .enable(CsvGenerator.Feature.STRICT_CHECK_FOR_QUOTING)
}
