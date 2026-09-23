package trium.validator.policy;

import static trium.validator.policy.ImageViolationException.Reason.AREA_CALCULATION_OVERFLOW;
import static trium.validator.policy.ImageViolationException.Reason.INVALID_DIMENSIONS;
import static trium.validator.policy.ImageViolationException.Reason.MAX_AREA_EXCEEDED;
import static trium.validator.policy.ImageViolationException.Reason.MAX_HEIGHT_EXCEEDED;
import static trium.validator.policy.ImageViolationException.Reason.MAX_RAM_EXCEEDED;
import static trium.validator.policy.ImageViolationException.Reason.MAX_WIDTH_EXCEEDED;
import static trium.validator.policy.ImageViolationException.Reason.MIN_AREA_NOT_MET;
import static trium.validator.policy.ImageViolationException.Reason.MIN_HEIGHT_NOT_MET;
import static trium.validator.policy.ImageViolationException.Reason.MIN_WIDTH_NOT_MET;
import static trium.validator.policy.ImageViolationException.Reason.RAM_CALCULATION_OVERFLOW;

import java.util.Objects;
import java.util.Optional;

import trium.validator.FileCombinators;

// @formatter:off
/// Defines the security limits and input constraints for image dimensions and memory usage.
///
/// Image dimensions policies can be combined
/// using {@link #intersect}, {@link #span}, and {@link #exactlyOne}.
///
/// The combinations can also be guarded using {@link FileCombinators#guard},
/// and predicates such as {@link #sameBytesPerUnitEstimate}.
///
/// An image dimensions policy is considered dummy
/// when no image dimension or memory restriction is enforced, as indicated by {@link #isDummy()}.
// @formatter:on
public record ImageDimensions(
        int minWidth,
        int maxWidth,
        int minHeight,
        int maxHeight,
        long minArea,
        long maxArea,
        long maxUncompressedRam,
        long bytesPerUnitEstimate) {
    // @formatter:off
    /// Indicates that no limit is enforced for the corresponding constraint.
    ///
    /// This value must not be used as an unlimited sentinel for {@code long} constraints.
    // @formatter:on
    public static final int MAX_UNLIMITED_INT = Integer.MAX_VALUE;

    /// Indicates that no limit is enforced for the corresponding constraint.
    public static final long MAX_UNLIMITED = Long.MAX_VALUE;

    /// Default estimated number of bytes per unit, based on an RGBA model.
    public static final long DEFAULT_BYTES_PER_UNIT_ESTIMATE = 4L;

    /// Represents an unconstrained image dimensions policy
    /// with the default bytes-per-unit estimate.
    public static final ImageDimensions DUMMY = new ImageDimensions(
            0,
            MAX_UNLIMITED_INT,
            0,
            MAX_UNLIMITED_INT,
            0,
            MAX_UNLIMITED,
            MAX_UNLIMITED,
            DEFAULT_BYTES_PER_UNIT_ESTIMATE);

    // @formatter:off
    /// Use {@link #of(int, int, int, int, long, long, long, long)}
    /// instead of this constructor for efficiency.
    ///
    /// @param minWidth             minimum allowed image width
    /// @param maxWidth             maximum allowed image width
    /// @param minHeight            minimum allowed image height
    /// @param maxHeight            maximum allowed image height
    /// @param minArea              minimum allowed image area (width x height)
    /// @param maxArea              maximum allowed image area (width x height)
    /// @param maxUncompressedRam   maximum estimated RAM usage in bytes after decoding unit data
    /// @param bytesPerUnitEstimate estimated byte multiplier per unit used to calculate memory usage
    /// @throws IllegalArgumentException if numeric thresholds are invalid,
    ///                                  or {@code bytesPerUnitEstimate} is outside 1 to 32
    /// @see #of(int, int, int, int, long, long, long)
    // @formatter:on
    public ImageDimensions {
        if (minWidth <= 0) {
            throw new IllegalArgumentException("minWidth must be positive");
        }
        if (minWidth == MAX_UNLIMITED_INT) {
            throw new IllegalArgumentException("minWidth must not be unlimited");
        }
        if (maxWidth < minWidth) {
            throw new IllegalArgumentException("maxWidth must not be less than minWidth");
        }
        if (minHeight <= 0) {
            throw new IllegalArgumentException("minHeight must be positive");
        }
        if (minHeight == MAX_UNLIMITED_INT) {
            throw new IllegalArgumentException("minHeight must not be unlimited");
        }
        if (maxHeight < minHeight) {
            throw new IllegalArgumentException("maxHeight must not be less than minHeight");
        }
        if (minArea <= 0) {
            throw new IllegalArgumentException("minArea must be positive");
        }
        if (minArea == MAX_UNLIMITED) {
            throw new IllegalArgumentException("minArea must not be unlimited");
        }
        if (maxArea < minArea) {
            throw new IllegalArgumentException("maxArea must not be less than minArea");
        }
        if (maxUncompressedRam <= 0) {
            throw new IllegalArgumentException("maxUncompressedRam must be positive");
        }
        if (bytesPerUnitEstimate < 1 || bytesPerUnitEstimate > 32) {
            throw new IllegalArgumentException("bytesPerUnitEstimate must be between 1 and 32");
        }
    }

    /// Use this method instead of the constructor for efficiency.
    ///
    /// @see #of(int, int, int, int, long, long, long)
    /// @see #ImageDimensions(int, int, int, int, long, long, long, long)
    /// @see #DUMMY
    public static ImageDimensions of(
            int minWidth,
            int maxWidth,
            int minHeight,
            int maxHeight,
            long minArea,
            long maxArea,
            long maxUncompressedRam,
            long bytesPerUnitEstimate) {
        if (minWidth == 0
                && maxWidth == MAX_UNLIMITED_INT
                && minHeight == 0
                && maxHeight == MAX_UNLIMITED_INT
                && minArea == 0
                && maxArea == MAX_UNLIMITED
                && maxUncompressedRam == MAX_UNLIMITED
                && bytesPerUnitEstimate == DEFAULT_BYTES_PER_UNIT_ESTIMATE) {
            return DUMMY;
        }

        return new ImageDimensions(
                minWidth,
                maxWidth,
                minHeight,
                maxHeight,
                minArea,
                maxArea,
                maxUncompressedRam,
                bytesPerUnitEstimate);
    }

    /// Constructs an {@code ImageDimensions} defaulting
    /// {@code bytesPerUnitEstimate} to 4 bytes (RGBA model).
    ///
    /// @see #of(int, int, int, int, long, long, long, long)
    public static ImageDimensions of(
            int minWidth,
            int maxWidth,
            int minHeight,
            int maxHeight,
            long minArea,
            long maxArea,
            long maxUncompressedRam) {
        return ImageDimensions.of(
                minWidth,
                maxWidth,
                minHeight,
                maxHeight,
                minArea,
                maxArea,
                maxUncompressedRam,
                DEFAULT_BYTES_PER_UNIT_ESTIMATE);
    }

    /// Indicates whether no image dimension or memory restriction is enforced.
    ///
    /// @return {@code true} if all limits are unlimited
    /// @see MAX_UNLIMITED_INT
    /// @see MAX_UNLIMITED
    public boolean isDummy() {
        return minWidth == 0
                && maxWidth == MAX_UNLIMITED_INT
                && minHeight == 0
                && maxHeight == MAX_UNLIMITED_INT
                && minArea == 0
                && maxArea == MAX_UNLIMITED
                && maxUncompressedRam == MAX_UNLIMITED;
    }

    // @formatter:off
    /// Validates the image dimensions against the configured security limits.
    ///
    /// Checks the minimum and maximum width, height, area, and estimated
    /// uncompressed memory footprint before the image is decoded.
    ///
    /// @param width  the image width
    /// @param height the image height
    /// @throws ImageViolationException if {@code width} or {@code height} is not positive
    ///                                 ({@link ImageViolationException.Reason#INVALID_DIMENSIONS}),
    ///                                 if {@code width} is below the minimum
    ///                                 ({@link ImageViolationException.Reason#MIN_WIDTH_NOT_MET}),
    ///                                 if {@code width} exceeds the limit
    ///                                 ({@link ImageViolationException.Reason#MAX_WIDTH_EXCEEDED}),
    ///                                 if {@code height} is below the minimum
    ///                                 ({@link ImageViolationException.Reason#MIN_HEIGHT_NOT_MET}),
    ///                                 if {@code height} exceeds the limit
    ///                                 ({@link ImageViolationException.Reason#MAX_HEIGHT_EXCEEDED}),
    ///                                 if total area is below the minimum
    ///                                 ({@link ImageViolationException.Reason#MIN_AREA_NOT_MET}),
    ///                                 if total area exceeds the limit
    ///                                 ({@link ImageViolationException.Reason#MAX_AREA_EXCEEDED}),
    ///                                 if estimated RAM exceeds the limit
    ///                                 ({@link ImageViolationException.Reason#MAX_RAM_EXCEEDED}),
    ///                                 or if an arithmetic overflow occurs during area calculation
    ///                                 ({@link ImageViolationException.Reason#AREA_CALCULATION_OVERFLOW}),
    ///                                 or if an arithmetic overflow occurs during RAM calculation
    ///                                 ({@link ImageViolationException.Reason#RAM_CALCULATION_OVERFLOW})
    // @formatter:on
    public void validate(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new ImageViolationException(INVALID_DIMENSIONS);
        }
        if (width < minWidth) {
            throw new ImageViolationException(MIN_WIDTH_NOT_MET);
        }
        if (width > maxWidth) {
            throw new ImageViolationException(MAX_WIDTH_EXCEEDED);
        }
        if (height < minHeight) {
            throw new ImageViolationException(MIN_HEIGHT_NOT_MET);
        }
        if (height > maxHeight) {
            throw new ImageViolationException(MAX_HEIGHT_EXCEEDED);
        }

        long area;
        try {
            area = Math.multiplyExact((long) width, (long) height);
        } catch (ArithmeticException e) {
            throw new ImageViolationException(AREA_CALCULATION_OVERFLOW);
        }

        if (area < minArea) {
            throw new ImageViolationException(MIN_AREA_NOT_MET);
        }
        if (area > maxArea) {
            throw new ImageViolationException(MAX_AREA_EXCEEDED);
        }

        long estimatedRam;
        try {
            estimatedRam = Math.multiplyExact(area, bytesPerUnitEstimate);
        } catch (ArithmeticException e) {
            throw new ImageViolationException(RAM_CALCULATION_OVERFLOW);
        }

        if (estimatedRam > maxUncompressedRam) {
            throw new ImageViolationException(MAX_RAM_EXCEEDED);
        }
    }

    // @formatter:off
    /// Returns the intersection of the given image dimensions policies.
    ///
    /// The resulting policy applies the stricter minimum and maximum constraints
    /// for width, height, area, and uncompressed RAM.
    ///
    /// @param a the first
    /// @param b the second
    /// @return an {@link Optional} containing the intersection of {@code a} and {@code b},
    ///         or {@link Optional#empty()} if the policies do not overlap
    /// @throws NullPointerException if {@code a} or {@code b} is {@code null}
    // @formatter:on
    public static Optional<ImageDimensions> intersect(
            ImageDimensions a,
            ImageDimensions b) {
        Objects.requireNonNull(a, "a must not be null");
        Objects.requireNonNull(b, "b must not be null");

        int minWidth = Math.max(a.minWidth(), b.minWidth());
        int maxWidth = Math.min(a.maxWidth(), b.maxWidth());

        int minHeight = Math.max(a.minHeight(), b.minHeight());
        int maxHeight = Math.min(a.maxHeight(), b.maxHeight());

        long minArea = Math.max(a.minArea(), b.minArea());
        long maxArea = Math.min(a.maxArea(), b.maxArea());

        long maxUncompressedRam = Math.min(a.maxUncompressedRam(), b.maxUncompressedRam());

        long bytesPerUnitEstimate = Math.max(
                a.bytesPerUnitEstimate(),
                b.bytesPerUnitEstimate());

        if (minWidth > maxWidth
                || minHeight > maxHeight
                || minArea > maxArea) {
            return Optional.empty();
        }

        return Optional.of(ImageDimensions.of(
                minWidth,
                maxWidth,
                minHeight,
                maxHeight,
                minArea,
                maxArea,
                maxUncompressedRam,
                bytesPerUnitEstimate));
    }

    // @formatter:off
    /// Spans two image dimensions policies by producing
    /// the smallest policy that contains both.
    ///
    /// The resulting minimum for each constrained dimension is the lesser of the two minimums,
    /// and the resulting maximum is the greater of the two maximums.
    ///
    /// @param a the first
    /// @param b the second
    /// @return the span of the two image dimensions policies
    /// @throws NullPointerException if {@code a} or {@code b} is {@code null}
    /// @see #optionalOfSpan(ImageDimensions, ImageDimensions)
    // @formatter:on
    public static ImageDimensions span(
            ImageDimensions a,
            ImageDimensions b) {
        Objects.requireNonNull(a, "a must not be null");
        Objects.requireNonNull(b, "b must not be null");

        int minWidth = Math.min(a.minWidth(), b.minWidth());
        int maxWidth = Math.max(a.maxWidth(), b.maxWidth());

        int minHeight = Math.min(a.minHeight(), b.minHeight());
        int maxHeight = Math.max(a.maxHeight(), b.maxHeight());

        long minArea = Math.min(a.minArea(), b.minArea());
        long maxArea = Math.max(a.maxArea(), b.maxArea());

        long maxUncompressedRam = Math.max(
                a.maxUncompressedRam(),
                b.maxUncompressedRam());

        long bytesPerUnitEstimate = Math.max(
                a.bytesPerUnitEstimate(),
                b.bytesPerUnitEstimate());

        return ImageDimensions.of(
                minWidth,
                maxWidth,
                minHeight,
                maxHeight,
                minArea,
                maxArea,
                maxUncompressedRam,
                bytesPerUnitEstimate);
    }

    /// ~
    ///
    /// @see #span(ImageDimensions, ImageDimensions)
    public static Optional<ImageDimensions> optionalOfSpan(
            ImageDimensions a,
            ImageDimensions b) {
        return Optional.of(span(a, b));
    }

    // @formatter:off
    /// Returns the non-dummy image dimensions policy
    /// when exactly one of the policies is non-dummy.
    ///
    /// @param a the first
    /// @param b the second
    /// @return the non-dummy image dimensions policy,
    ///         or {@link Optional#empty()} if both policies are dummy or both are non-dummy
    /// @throws NullPointerException if {@code a} or {@code b} is {@code null}
    // @formatter:on
    public static Optional<ImageDimensions> exactlyOne(
            ImageDimensions a,
            ImageDimensions b) {
        Objects.requireNonNull(a, "a must not be null");
        Objects.requireNonNull(b, "b must not be null");

        if (a.isDummy() == b.isDummy()) {
            return Optional.empty();
        }

        return Optional.of(a.isDummy() ? b : a);
    }

    /// Returns whether the given image dimensions policies
    /// use the same bytes-per-unit estimate.
    ///
    /// This predicate can be used with {@link FileCombinators#guard}
    /// to guard policy combinations that require matching estimates.
    ///
    /// @throws NullPointerException if {@code a} or {@code b} is {@code null}
    public static boolean sameBytesPerUnitEstimate(
            ImageDimensions a,
            ImageDimensions b) {
        Objects.requireNonNull(a, "a must not be null");
        Objects.requireNonNull(b, "b must not be null");

        return a.bytesPerUnitEstimate() == b.bytesPerUnitEstimate();
    }
}
