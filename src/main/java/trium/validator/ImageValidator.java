package trium.validator;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.stream.Stream;

import trium.image.ImageInfo;
import trium.image.ImageInspector;
import trium.validator.policy.FileSize;
import trium.validator.policy.FileType;
import trium.validator.policy.FileTypeMapping;
import trium.validator.policy.ImageDimensions;

// @formatter:off
/// Validates image files against configured file size, file type, and image dimensions policies.
///
/// At least one validation policy must be defined.
///
/// Validation can be performed in two stages: file size and metadata can be validated first
/// using {@link #validateFileSizeAndMetadata},
/// followed by image format and dimensions using {@link #validateImageInfo}.
///
/// Strict validation requires all relevant inputs and validation policies to be present and effective.
///
/// Lax validation allows inputs and policies to be omitted
/// when they are not required for the requested validation.
///
/// The validator may be combined with another validator using
/// {@link #exactlyOneOf(ImageValidator, ImageValidator)},
/// {@link #intersect(boolean, ImageValidator, ImageValidator)},
/// {@link #relax(boolean, ImageValidator, ImageValidator)},
/// or, more generally, {@link #combine}.
///
/// The file validator may be absent, indicating that file validation is not enforced.
///
/// When present, its file size policy may be dummy ({@link FileSize#isDummy()})
/// and its file type policy may be absent,
/// indicating that the corresponding file validation is not enforced.
///
/// The image dimensions policy may be dummy ({@link ImageDimensions#isDummy()}),
/// indicating that image dimensions validation is not enforced.
///
// @formatter:on
public record ImageValidator(
                Optional<FileValidator> fileValidator,
                ImageDimensions imageDimensions) {
        /// ~
        ///
        /// @throws NullPointerException          if any argument is {@code null}
        /// @throws NoImagePolicyDefinedException if no image validation policies
        ///                                       are defined
        /// @see #ImageValidator(FileValidator, ImageDimensions)
        public ImageValidator {
                Objects.requireNonNull(fileValidator, "fileValidator must not be null");
                Objects.requireNonNull(imageDimensions, "imageDimensions must not be null");

                if (fileValidator.isEmpty() && imageDimensions.isDummy()) {
                        throw new NoImagePolicyDefinedException(
                                        "at least one validation policy must be defined");
                }
        }

        public ImageValidator(
                        FileValidator fileValidator,
                        ImageDimensions imageDimensions) {
                this(Optional.of(fileValidator), imageDimensions);
        }

        public ImageValidator(FileValidator fileValidator) {
                this(fileValidator, ImageDimensions.DUMMY);
        }

        public ImageValidator(ImageDimensions imageDimensions) {
                this(Optional.empty(), imageDimensions);
        }

        // @formatter:off
        /// Combines two {@link ImageValidator}s
        /// using the provided combiners for file size, file type, and image dimensions.
        ///
        /// In strict mode, all combinations must succeed and produce effective policies.
        /// Dummy policies are treated as absent.
        ///
        /// In non-strict mode, the resulting validator may contain any successful combinations.
        ///
        /// @param strict whether all combinations must succeed and produce effective policies
        /// @param sizeCombiner the strategy to combine {@link FileSize}s
        /// @param typeCombiner the strategy to combine {@link FileType}s
        /// @param imageCombiner the strategy to combine {@link ImageDimensions}
        /// @param a the first
        /// @param b the second
        /// @return the combined validator,
        ///         or {@link Optional#empty()} if no effective policies remain after combination
        /// @throws NullPointerException if any argument is {@code null}
        // @formatter:on
        public static Optional<ImageValidator> combine(
                        boolean strict,
                        BiFunction<FileSize, FileSize, Optional<FileSize>> sizeCombiner,
                        BinaryOperator<Optional<FileType>> typeCombiner,
                        BiFunction<ImageDimensions, ImageDimensions, Optional<ImageDimensions>> imageCombiner,
                        ImageValidator a,
                        ImageValidator b) {
                Objects.requireNonNull(a, "a must not be null");
                Objects.requireNonNull(b, "b must not be null");
                Objects.requireNonNull(sizeCombiner, "sizeCombiner must not be null");
                Objects.requireNonNull(typeCombiner, "typeCombiner must not be null");
                Objects.requireNonNull(imageCombiner, "imageCombiner must not be null");

                var imageDimensions = imageCombiner.apply(a.imageDimensions(), b.imageDimensions());

                if (strict && imageDimensions.isEmpty()) {
                        return Optional.empty();
                }

                var fileSize = sizeCombiner.apply(
                                a.fileValidator().map(FileValidator::fileSize).orElse(FileSize.DUMMY),
                                b.fileValidator().map(FileValidator::fileSize).orElse(FileSize.DUMMY));

                var fileType = typeCombiner.apply(
                                a.fileValidator().flatMap(FileValidator::fileType),
                                b.fileValidator().flatMap(FileValidator::fileType));

                var effectiveSize = fileSize.filter(s -> !s.isDummy());
                var effectiveDimensions = imageDimensions.filter(d -> !d.isDummy());

                if (strict) {
                        return effectiveDimensions.flatMap(
                                        d -> effectiveSize.flatMap(
                                                        s -> fileType.map(
                                                                        t -> new ImageValidator(
                                                                                        new FileValidator(s, t),
                                                                                        d))));
                }

                if (effectiveSize.isPresent() || fileType.isPresent() || effectiveDimensions.isPresent()) {
                        Optional<FileValidator> fileValidator = effectiveSize.isPresent() || fileType.isPresent()
                                        ? Optional.of(new FileValidator(effectiveSize.orElse(FileSize.DUMMY), fileType))
                                        : Optional.empty();

                        return Optional.of(new ImageValidator(
                                        fileValidator,
                                        effectiveDimensions.orElse(ImageDimensions.DUMMY)));
                }

                return Optional.empty();
        }

        /// Combines two {@link ImageValidator}s by retaining
        /// the non-dummy file size and image dimensions and non-empty file type
        /// when exactly one of each is present.
        ///
        /// One validator must provide two policies and the other must provide one.
        ///
        /// @param a the first
        /// @param b the second
        /// @return the resulting image validator,
        ///         or {@link Optional#empty()} if the validators do not contain
        ///         exactly one non-dummy file size and image dimensions
        ///         and exactly one non-empty file type
        /// @throws NullPointerException if {@code a} or {@code b} is {@code null}
        /// @see #combine
        /// @see #exactlyOneOf(ImageValidator, ImageValidator, ImageValidator)
        public static Optional<ImageValidator> exactlyOneOf(ImageValidator a, ImageValidator b) {
                return combine(
                                true,
                                FileSize::exactlyOne,
                                FileType::exactlyOne,
                                ImageDimensions::exactlyOne,
                                a,
                                b);
        }

        /// Combines three {@link ImageValidator}s by retaining
        /// the non-dummy file size and image dimensions and non-empty file type
        /// when exactly one of each is present in each of the three validators.
        ///
        /// @param a the first
        /// @param b the second
        /// @param c the third
        /// @return the resulting image validator,
        ///         or {@link Optional#empty()} if the validators do not contain
        ///         exactly one non-dummy file size and image dimensions
        ///         and exactly one non-empty file type
        /// @throws NullPointerException if any argument is {@code null}
        /// @see #exactlyOneOf(ImageValidator, ImageValidator)
        public static Optional<ImageValidator> exactlyOneOf(
                        ImageValidator a,
                        ImageValidator b,
                        ImageValidator c) {
                Objects.requireNonNull(a, "a must not be null");
                Objects.requireNonNull(b, "b must not be null");
                Objects.requireNonNull(c, "c must not be null");

                var fileSizes = Stream.of(a, b, c)
                                .map(ImageValidator::fileValidator)
                                .flatMap(Optional::stream)
                                .map(FileValidator::fileSize)
                                .filter(s -> !s.isDummy())
                                .toList();

                var fileTypes = Stream.of(a, b, c)
                                .map(ImageValidator::fileValidator)
                                .flatMap(Optional::stream)
                                .map(FileValidator::fileType)
                                .flatMap(Optional::stream)
                                .toList();

                var dimensions = Stream.of(a, b, c)
                                .map(ImageValidator::imageDimensions)
                                .filter(d -> !d.isDummy())
                                .toList();

                if (fileSizes.size() != 1 || fileTypes.size() != 1 || dimensions.size() != 1) {
                        return Optional.empty();
                }

                var fileValidator = new FileValidator(fileSizes.getFirst(), fileTypes.getFirst());

                return Optional.of(
                                new ImageValidator(fileValidator, dimensions.getFirst()));
        }

    // @formatter:off
    /// Returns the intersection of the given {@link ImageValidator}s.
    ///
    /// In strict mode, all intersections must succeed.
    /// In non-strict mode, the resulting validator may contain any successful intersections.
    ///
    /// @param strict whether all intersections must succeed
    /// @param a the first
    /// @param b the second
    /// @return the intersected image validator,
    ///         or {@link Optional#empty()} if no effective policies remain after intersection
    /// @throws NullPointerException if {@code a} or {@code b} is {@code null}
    /// @see #combine
    /// @see FileSize#intersect
    /// @see FileType#intersect
    /// @see ImageDimensions#intersect
    // @formatter:on
        public static Optional<ImageValidator> intersect(
                        boolean strict,
                        ImageValidator a,
                        ImageValidator b) {
                return combine(
                                strict,
                                FileSize::intersect,
                                FileType::intersect,
                                ImageDimensions::intersect,
                                a,
                                b);
        }

        /// Returns the strict intersection of the given {@link ImageValidator}s.
        ///
        /// @see #intersect(boolean, ImageValidator, ImageValidator)
        public static Optional<ImageValidator> strictIntersect(
                        ImageValidator a,
                        ImageValidator b) {
                return intersect(true, a, b);
        }

        /// Returns the lax intersection of the given {@link ImageValidator}s.
        ///
        /// @see #intersect(boolean, ImageValidator, ImageValidator)
        public static Optional<ImageValidator> laxIntersect(
                        ImageValidator a,
                        ImageValidator b) {
                return intersect(false, a, b);
        }

    // @formatter:off
    /// Returns the relaxation of the given {@link ImageValidator}s.
    ///
    /// When {@code requireAll} is {@code true}, all relaxations must succeed.
    /// Otherwise, the resulting validator may contain any successful relaxation,
    /// with dummy policies used when the file size or image dimensions relaxation fails.
    ///
    /// @param requireAll whether all relaxations must succeed
    /// @param a the first
    /// @param b the second
    /// @return the relaxed image validator,
    ///         or {@link Optional#empty()} if no effective policies remain after relaxation
    /// @throws NullPointerException if any argument is {@code null}
    /// @see #combine
    /// @see FileSize#span
    /// @see FileType#union
    /// @see ImageDimensions#span
    // @formatter:on
        public static Optional<ImageValidator> relax(
                        boolean requireAll,
                        ImageValidator a,
                        ImageValidator b) {
                return combine(
                                requireAll,
                                FileSize::optionalOfSpan,
                                FileType::union,
                                ImageDimensions::optionalOfSpan,
                                a,
                                b);
        }

        /// Validates the file size and metadata against the configured policies.
        ///
        /// @see FileValidator#validateFileSizeAndMetadata
        public List<FileTypeMapping> validateFileSizeAndMetadata(
                        boolean strict,
                        OptionalLong size,
                        Optional<String> extension,
                        Optional<String> mimeType) {
                if (fileValidator.isEmpty()) {
                        throw new IllegalStateException("file validation policy is required");
                }

                return fileValidator.get()
                                .validateFileSizeAndMetadata(strict, size, extension, mimeType);
        }

        /// Validates the file size and metadata in strict mode.
        ///
        /// @see #validateFileSizeAndMetadata(boolean, OptionalLong, Optional, Optional)
        public List<FileTypeMapping> strictValidateFileSizeAndMetadata(
                        OptionalLong size,
                        Optional<String> extension,
                        Optional<String> mimeType) {
                return validateFileSizeAndMetadata(true, size, extension, mimeType);
        }

        /// Validates the file size and metadata in lax mode.
        ///
        /// @see #validateFileSizeAndMetadata(boolean, OptionalLong, Optional, Optional)
        public List<FileTypeMapping> laxValidateFileSizeAndMetadata(
                        OptionalLong size,
                        Optional<String> extension,
                        Optional<String> mimeType) {
                return validateFileSizeAndMetadata(false, size, extension, mimeType);
        }

    // @formatter:off
    /// Validates image format and dimensions against the configured policies.
    ///
    /// This method may be used as the second step of a validation flow,
    /// following {@link #validateFileSizeAndMetadata}.
    ///
    /// In strict mode, format, width, and height must all be provided,
    /// and the image dimensions and file type policies must not be dummy or absent.
    ///
    /// In lax mode, each provided value is validated when the corresponding policy is available;
    /// an {@link IllegalStateException} is thrown when a provided value requires an unavailable policy.
    ///
    /// Width and height must either both be provided or both be empty.
    ///
    /// @param strict whether all required inputs and validation policies must be present
    /// @param candidates the file type mappings to validate against
    /// @param format the image format
    /// @param width the image width
    /// @param height the image height
    /// @throws IllegalStateException if the required validation policies are unavailable
    ///                               for the provided inputs
    /// @throws IllegalArgumentException if strict validation is requested without
    ///                                  format, width, or height,
    ///                                  or if only one of width and height is provided
    /// @throws NullPointerException if any argument is {@code null}
    /// @see ImageDimensions#validate(int, int)
    /// @see FileValidator#validateFormat
    // @formatter:on
        public void validateImageInfo(
                        boolean strict,
                        List<FileTypeMapping> candidates,
                        Optional<String> format,
                        OptionalInt width,
                        OptionalInt height) {
                if (strict
                                && (imageDimensions.isDummy()
                                                || fileValidator.isEmpty()
                                                || fileValidator.get().fileType().isEmpty())) {
                        throw new IllegalStateException(
                                        "strict validation requires image dimensions and file type policies");
                }

                Objects.requireNonNull(candidates, "candidates must not be null");
                Objects.requireNonNull(format, "format must not be null");
                Objects.requireNonNull(width, "width must not be null");
                Objects.requireNonNull(height, "height must not be null");

                if (strict && !(format.isPresent() && width.isPresent() && height.isPresent())) {
                        throw new IllegalArgumentException(
                                        "format, width, and height must all be present in strict mode");
                }

                if (format.isPresent()
                                && (fileValidator.isEmpty()
                                                || fileValidator.get().fileType().isEmpty())) {
                        throw new IllegalStateException(
                                        "file type policy is required when format is provided");
                }

                if (width.isPresent() != height.isPresent()) {
                        throw new IllegalArgumentException(
                                        "width and height must either both be present or both be absent");
                }

                if (width.isPresent() && height.isPresent() && imageDimensions.isDummy()) {
                        throw new IllegalStateException(
                                        "image dimensions policy must not be dummy when dimensions are provided");
                }

                if (width.isPresent() && height.isPresent()) {
                        imageDimensions.validate(width.getAsInt(), height.getAsInt());
                }

                if (format.isPresent()) {
                        fileValidator.get().strictValidateFormat(candidates, format);
                }
        }

        /// Validates image format and dimensions in strict mode.
        ///
        /// @see #validateImageInfo(boolean, List, Optional, OptionalInt, OptionalInt)
        public void strictValidateImageInfo(
                        List<FileTypeMapping> candidates,
                        Optional<String> format,
                        OptionalInt width,
                        OptionalInt height) {
                validateImageInfo(true, candidates, format, width, height);
        }

        /// Validates image format and dimensions in lax mode.
        ///
        /// @see #validateImageInfo(boolean, List, Optional, OptionalInt, OptionalInt)
        public void laxValidateImageInfo(
                        List<FileTypeMapping> candidates,
                        Optional<String> format,
                        OptionalInt width,
                        OptionalInt height) {
                validateImageInfo(false, candidates, format, width, height);
        }

    // @formatter:off
    /// Inspects the input using {@link ImageInspector} and validates its image format and dimensions
    /// using {@link #validateImageInfo(boolean, List, Optional, OptionalInt, OptionalInt)} against
    /// the configured policies in strict mode.
    ///
    /// @param candidates the file type mappings to validate against
    /// @param input the image input to inspect and validate
    /// @throws NullPointerException if {@code candidates} or {@code input} is {@code null}
    /// @throws IllegalStateException if the required validation policies are unavailable
    ///                               for strict validation
    /// @throws IOException if an I/O error occurs, if the stream does not contain recognizable
    ///                     image data, or if no registered reader supports the format
    // @formatter:on
        public void validateImageInfo(
                        List<FileTypeMapping> candidates,
                        InputStream input) throws IOException {
                Objects.requireNonNull(candidates, "candidates must not be null");

                ImageInfo info = ImageInspector.inspect(input);

                strictValidateImageInfo(
                                candidates,
                                Optional.of(info.format()),
                                OptionalInt.of(info.width()),
                                OptionalInt.of(info.height()));
        }
}
