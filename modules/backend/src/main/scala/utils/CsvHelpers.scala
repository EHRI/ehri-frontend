package utils

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.connectors.csv.scaladsl.CsvFormatting
import org.apache.pekko.stream.scaladsl.Source

trait CsvHelpers {
  def writeCsv(headers: Seq[String], data: Seq[Array[String]], sep: Char = ','): Source[String, NotUsed] = {
    val s: Seq[Seq[String]] = headers +: data.map(_.toSeq)
    val src: Source[Seq[String], NotUsed] = Source.apply(s.toList)
    val csvFormat = CsvFormatting.format(delimiter = sep)
    src.via(csvFormat).map(_.utf8String)
  }
}

object CsvHelpers extends CsvHelpers
