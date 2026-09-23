package trium.validator;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Consumer;

import trium.validator.policy.FileSize;
import trium.validator.policy.FileType;
import trium.validator.policy.FileTypeMapping;
import trium.validator.policy.ImageDimensions;

/// Defines the image validation configuration for file and image dimensions.
///
/// At least one validation policy must be defined.
///
/// The configuration is used to create an {@link ImageValidator}.
///
/// The file validation configuration is defined by {@link FileConfig}.
public class ImageConfig {
    /// Prefix for image dimensions configuration properties.
    public static final String IMAGE_DIMENSIONS_PREFIX = "image.";

    /// String representation of an unlimited maximum value
    /// in configuration properties.
    public static final String MAX_UNLIMITED_STR = "unlimited";

    private ImageConfig() {
    }

    // @formatter:off
    /// Loads image validation configuration from the given properties.
    ///
    /// Missing properties are logged when a logger is provided.
    ///
    /// Validation of configured values is limited to the constraints enforced by
    /// {@link FileSize}, {@link FileTypeMapping}, {@link FileType}, and {@link ImageDimensions}.
    ///
    /// @param log optional logger for reporting missing configuration properties
    /// @param properties the properties containing the image validation configuration
    /// @return the loaded {@link ImageValidator}
    /// @throws NullPointerException if {@code log} or {@code properties} is {@code null}
    /// @throws IllegalArgumentException if a configured value is invalid
    /// @throws NoImagePolicyDefinedException if no configuration is provided
    /// @see #load(InputStream)
    /// @see #load(Properties)
    /// @see FileConfig
    // @formatter:on
    public static ImageValidator load(Optional<Consumer<String>> log, Properties properties) {
        Objects.requireNonNull(log, "log must not be null");
        Objects.requireNonNull(properties, "properties must not be null");

        Optional<FileValidator> fileValidator = Optional.empty();
        try {
            fileValidator = Optional.of(FileConfig.load(log, properties));
        } catch (NoFilePolicyDefinedException e) {
            // no file configuration provided
        }

        var minWidth = parseInt(
                log,
                properties,
                IMAGE_DIMENSIONS_PREFIX + "min-width");
        var maxWidth = parseInt(
                log,
                properties,
                IMAGE_DIMENSIONS_PREFIX + "max-width");
        var minHeight = parseInt(
                log,
                properties,
                IMAGE_DIMENSIONS_PREFIX + "min-height");
        var maxHeight = parseInt(
                log,
                properties,
                IMAGE_DIMENSIONS_PREFIX + "max-height");
        var minArea = parseLong(
                log,
                properties,
                IMAGE_DIMENSIONS_PREFIX + "min-area");
        var maxArea = parseLong(
                log,
                properties,
                IMAGE_DIMENSIONS_PREFIX + "max-area");
        var maxUncompressedRam = parseLong(
                log,
                properties,
                IMAGE_DIMENSIONS_PREFIX + "max-uncompressed-ram");

        var bytesPerUnitEstimateRaw = properties.getProperty(
                IMAGE_DIMENSIONS_PREFIX + "bytes-per-unit-estimate");

        ImageDimensions imageDimensions;
        if (bytesPerUnitEstimateRaw == null) {
            log.ifPresent(o -> o.accept("%s is not configured".formatted(
                    IMAGE_DIMENSIONS_PREFIX + "bytes-per-unit-estimate")));

            imageDimensions = ImageDimensions.of(
                    minWidth,
                    maxWidth,
                    minHeight,
                    maxHeight,
                    minArea,
                    maxArea,
                    maxUncompressedRam);
        } else {
            try {
                long bytesPerUnitEstimate = Long.parseLong(bytesPerUnitEstimateRaw);
                imageDimensions = ImageDimensions.of(
                        minWidth,
                        maxWidth,
                        minHeight,
                        maxHeight,
                        minArea,
                        maxArea,
                        maxUncompressedRam,
                        bytesPerUnitEstimate);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "invalid %s".formatted(
                                IMAGE_DIMENSIONS_PREFIX + "bytes-per-unit-estimate"));
            }
        }

        if (imageDimensions.isDummy()) {
            log.ifPresent(o -> o.accept(IMAGE_DIMENSIONS_PREFIX + " is dummy"));
        }

        return new ImageValidator(fileValidator, imageDimensions);
    }

    /// Loads image validation configuration from the given properties
    /// without logging missing properties.
    ///
    /// @see #load(Optional, Properties)
    /// @see #load(InputStream)
    public static ImageValidator load(Properties properties) {
        return load(Optional.empty(), properties);
    }

    // @formatter:off
    /// Loads image validation configuration from the given input stream.
    ///
    /// The input stream is loaded into a {@link Properties} object and delegated to
    /// {@link #load(Optional, Properties)}.
    ///
    /// @param log   optional logger for reporting missing configuration properties
    /// @param input the input stream containing the image validation configuration
    /// @return the loaded {@link ImageValidator}
    /// @throws NullPointerException     if {@code log} or {@code input} is null
    /// @throws IOException              if an error occurred when reading from the
    ///                                  input stream
    /// @throws IllegalArgumentException if the input stream contains
    ///                                  a malformed Unicode escape sequence,
    ///                                  or if a configured value is invalid
    /// @throws NoImagePolicyDefinedException if no configuration is provided
    /// @see #load(InputStream)
    // @formatter:on
    public static ImageValidator load(Optional<Consumer<String>> log, InputStream input)
            throws IOException {
        Objects.requireNonNull(log, "log must not be null");
        Objects.requireNonNull(input, "input must not be null");

        var properties = new Properties();
        properties.load(input);

        return load(log, properties);
    }

    /// Loads image validation configuration from the given input stream
    /// without logging missing properties.
    ///
    /// Exceptions thrown during loading are documented
    /// by the corresponding overload.
    ///
    /// @see #load(Optional, InputStream)
    /// @see #load(Optional, Properties)
    public static ImageValidator load(InputStream input) throws IOException {
        return load(Optional.empty(), input);
    }

    // @formatter:off
    /// Parses a {@code int} configuration property.
    ///
    /// The {@code unlimited} value is interpreted as {@link ImageDimensions#MAX_UNLIMITED_INT}.
    /// Missing properties are logged when a logger is provided and interpreted as unlimited.
    ///
    /// @throws IllegalArgumentException if the configured value is invalid
    // @formatter:on
    private static int parseInt(
            Optional<Consumer<String>> log,
            Properties properties,
            String propertyName) {
        var value = properties.getProperty(propertyName);

        if (value == null) {
            log.ifPresent(o -> o.accept("%s is not configured".formatted(propertyName)));
            return ImageDimensions.MAX_UNLIMITED_INT;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            if (value.toLowerCase(Locale.ROOT).equals(MAX_UNLIMITED_STR)) {
                return ImageDimensions.MAX_UNLIMITED_INT;
            }

            throw new IllegalArgumentException("invalid %s".formatted(propertyName));
        }
    }

    // @formatter:off
    /// Parses a {@code long} configuration property.
    ///
    /// The {@code unlimited} value is interpreted as {@link ImageDimensions#MAX_UNLIMITED}.
    /// Missing properties are logged when a logger is provided and interpreted as unlimited.
    ///
    /// @throws IllegalArgumentException if the configured value is invalid
    // @formatter:on
    private static long parseLong(
            Optional<Consumer<String>> log,
            Properties properties,
            String propertyName) {
        var value = properties.getProperty(propertyName);

        if (value == null) {
            log.ifPresent(o -> o.accept("%s is not configured".formatted(propertyName)));
            return ImageDimensions.MAX_UNLIMITED;
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            if (value.toLowerCase(Locale.ROOT).equals(MAX_UNLIMITED_STR)) {
                return ImageDimensions.MAX_UNLIMITED;
            }

            throw new IllegalArgumentException("invalid %s".formatted(propertyName));
        }
    }
}
