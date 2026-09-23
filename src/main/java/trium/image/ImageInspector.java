package trium.image;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Objects;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

// @formatter:off
/// A utility class for safely and efficiently inspecting image files.
///
/// Extracts the image format, width and height in pixels by reading only the file headers,
/// without loading pixels into memory.
///
/// Format support relies on the underlying {@link javax.imageio.ImageIO} SPI registry.
/// Extended formats (like WebP) are supported transparently
/// if the respective plugins are available in the classpath.
// @formatter:on
public final class ImageInspector {
    private ImageInspector() {
    }

    // @formatter:off
    /// Inspects the provided input stream to extract the image's format, width, and height in pixels.
    ///
    /// Note that this method does not close the provided {@link InputStream}.
    ///
    /// @param input the {@link InputStream} containing the raw image data
    /// @return an {@link ImageInfo} object containing the format, width, and height
    /// @throws NullPointerException if the {@code input} is {@code null}
    /// @throws IOException if an I/O error occurs, if the stream does not contain recognizable
    ///                     image data, or if no registered reader supports the format
    // @formatter:on
    public static ImageInfo inspect(InputStream input) throws IOException {
        Objects.requireNonNull(input, "input must not be null");

        try (ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
            if (imageInput == null) {
                throw new IOException("unable to create image input stream");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);

            if (!readers.hasNext()) {
                throw new IOException("unsupported image format");
            }

            ImageReader reader = readers.next();

            try {
                reader.setInput(imageInput, true, true);

                return new ImageInfo(
                        reader.getFormatName(),
                        reader.getWidth(0),
                        reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        }
    }
}
