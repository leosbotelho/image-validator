package trium.image;

import java.util.Locale;
import java.util.Objects;

/// Contains basic information about an image.
public record ImageInfo(
        String format,
        int width,
        int height) {
    /// ~
    ///
    /// @throws NullPointerException     if {@code format} is {@code null}
    /// @throws IllegalArgumentException if {@code format} is blank,
    ///                                  {@code width} is not positive,
    ///                                  or {@code height} is not positive
    public ImageInfo {
        Objects.requireNonNull(format, "format must not be null");

        format = format.trim().toLowerCase(Locale.ROOT);

        if (format.isEmpty()) {
            throw new IllegalArgumentException("format must not be empty");
        }

        if (width <= 0) {
            throw new IllegalArgumentException("width must be positive");
        }

        if (height <= 0) {
            throw new IllegalArgumentException("height must be positive");
        }
    }
}
