package trium.validator.policy;

import java.util.Objects;

// @formatter:off
/// Exception thrown when an image fails one or more security policy validation checks.
///
/// Carries a specific {@link Reason} detailing the exact nature of the policy violation.
// @formatter:on
public class ImageViolationException extends RuntimeException {
    public enum Reason {
        // --- Dimension & Resource Validation ---
        INVALID_DIMENSIONS,
        MIN_WIDTH_NOT_MET,
        MAX_WIDTH_EXCEEDED,
        MIN_HEIGHT_NOT_MET,
        MAX_HEIGHT_EXCEEDED,
        MIN_AREA_NOT_MET,
        MAX_AREA_EXCEEDED,
        MAX_RAM_EXCEEDED,

        // --- Arithmetic Overflows ---
        AREA_CALCULATION_OVERFLOW,
        RAM_CALCULATION_OVERFLOW
    }

    private final Reason reason;

    public ImageViolationException(Reason reason) {
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
    }

    public Reason getReason() {
        return reason;
    }
}
