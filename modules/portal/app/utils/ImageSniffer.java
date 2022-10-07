package utils;

import org.apache.commons.io.FilenameUtils;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

/**
 * A utility class for retrieving information about images
 * without loading them into memory. Mostly adapted from:
 * https://stackoverflow.com/a/12164026/285374
 */
public class ImageSniffer {

    public static class UnsupportedImageTypeException extends Exception {
        public UnsupportedImageTypeException(String msg) {
            super(msg);
        }
    }

    public static long getTotalPixels(File file) throws IOException, UnsupportedImageTypeException {
        return getTotalPixels(file, file.getName());
    }
    public static long getTotalPixels(File file, String name) throws IOException, UnsupportedImageTypeException {
        final String extension = FilenameUtils.getExtension(name);
        final Iterator<ImageReader> readers = ImageIO.getImageReadersBySuffix(extension);
        if (readers.hasNext()) {
            ImageReader reader = readers.next();
            try (ImageInputStream stream = new FileImageInputStream(file)) {
                reader.setInput(stream);
                int width = reader.getWidth(reader.getMinIndex());
                int height = reader.getHeight(reader.getMinIndex());
                return (long) width * height;
            } finally {
                reader.dispose();
            }
        }
        throw new UnsupportedImageTypeException(
                String.format("Unable to find a reader for image type: '%s'", extension));
    }
}
