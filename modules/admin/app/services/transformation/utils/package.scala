package services.transformation

import models.DataTransformation

package object utils {

  /**
    * Given a string of input source and a set of transformations, calculate
    * a unique tag/digest for this operation.
    *
    * @param src      the input data, or a hash thereof
    * @param mappings a set of transform operations
    * @return an md5 string representing the overall operation
    */
  def digest(src: String, mappings: Seq[(DataTransformation.TransformationType.Value, String)]): String = {
    import java.math.BigInteger
    import java.security.MessageDigest

    // TODO: check a faster more robust way of generating a
    // reliable key for an input/maps pair?
    val digest = MessageDigest.getInstance("MD5")
    digest.update(src.getBytes)
    mappings.foreach { case (mapType, map) =>
      digest.update((mapType.toString + map).getBytes)
    }
    String.format("%032X", new BigInteger(1, digest.digest()))
  }
}
