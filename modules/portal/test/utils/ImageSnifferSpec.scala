package utils

import helpers.ResourceUtils
import play.api.test.PlaySpecification

class ImageSnifferSpec extends PlaySpecification with ResourceUtils {
  "ImageSniffer" should {
    "return the number of pixels in an image" in {
      val pngPixels = ImageSniffer.getTotalPixels(resourcePath("test-image.png").toFile)
      pngPixels must_== 26*23

      val jpgPixels = ImageSniffer.getTotalPixels(resourcePath("test-image.jpg").toFile)
      jpgPixels must_== 26*23
    }
    "throw an error with a non-image type" in {
      ImageSniffer.getTotalPixels(resourcePath("non-image.txt").toFile) must throwA[ImageSniffer.UnsupportedImageTypeException]
    }
  }
}
