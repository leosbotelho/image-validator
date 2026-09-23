# Image Validator

Java library for validating images against configurable security constraints.

## Motivation

Existing tools fall short for production-grade image validation due to three main limitations:
* **Isolated Checks & Lack of Consistency**: Libraries may provide validation for file attributes (extension, MIME type, format) and less commonly for image attributes (width, height, area, estimated uncompressed RAM), lacking configurable mechanisms for cross-attribute validation to ensure these metadata layers actually agree with one another.
* **Rigid Policies & Lack of Configurability**: Applications frequently need to reconcile general configurations and endpoint-specific constraints. Existing tools often lack configurable mechanisms for defining these policies and composing them, making it difficult to derive narrower or more permissive policies from existing configurations.
* **Resource Efficiency (Multi-Step Validation)**: Format and image dimensions detection can be computationally expensive, so cheaper checks can be performed first to narrow down the possible file types before detecting and validating the format and dimensions.

## Features

* Configurable and flexible validation policies
* Composable validation configurations, supporting both stricter and more permissive policies
* Multi-step file type validation
* Full control over validation scope, allowing selective checks for any combination of attributes
* File size validation with minimum and maximum limits
* File type validation using file extension, MIME type, and format, including consistency checks between them
* Image dimensions and estimated memory usage validation
* Configuration loading, with optional logger integration to report missing properties

## Configuration

Validation policies can be loaded from a properties file.

Not all configuration properties are required.

Example:
```properties
file-size.min=1
# 10 MiB
file-size.max=10485760

file-type.jpeg.extensions=jpg,jpeg
file-type.jpeg.mime-types=image/jpeg
file-type.jpeg.formats=JPEG

file-type.png.extensions=png
file-type.png.mime-types=image/png
file-type.png.formats=PNG

file-type.webp.extensions=webp
file-type.webp.mime-types=image/webp
file-type.webp.formats=WEBP

image.min-width=320
image.min-height=320
# Orientation-flexible 1920px bounding box, max 2.5 MP
image.max-width=1920
image.max-height=1920
image.max-pixels=2500000
```

The unlimited value can be used for image dimensions and memory constraints.

## Usage

Basic usage with a single configuration and multi-step validation:
```java
// Load configuration from a file
var imgValidator = ImageConfig.load(configInput);

// First step
var candidates = imgValidator.validateFileSizeAndMetadata(true, fileSize, extension, mimeType);

// Second step
imgValidator.validateImageInfo(candidates, imageInput);
```

You have full control over what is validated by calling the validation methods with the desired parameters.

Also available as aliases:
* `strictValidateFileSizeAndMetadata`
* `laxValidateFileSizeAndMetadata`

`validateImageInfo` can also be called directly with pre-extracted attributes (format, width, height) instead of reading from an input source.

<br>

Combining configurations loaded from separate files:
```java
// Load configuration from separate files
var sizeValidator = ImageConfig.load(sizeInput);
var typeValidator = ImageConfig.load(typeInput);
var dimensionsValidator = ImageConfig.load(dimensionsInput);

// Combine the configurations
Optional<ImageValidator> imgValidator =
        ImageValidator.exactlyOneOf(
                sizeValidator,
                typeValidator,
                dimensionsValidator);
```

This is also perfectly fine:
```java
// Load file validation configurations (returns FileValidator)
var sizeValidator = FileConfig.load(sizeInput);
var typeValidator = FileConfig.load(typeInput);

// Load image validation configuration (returns ImageValidator)
var dimensionsValidator = ImageConfig.load(dimensionsInput);

// Adapt and combine the configurations
Optional<ImageValidator> imgValidator =
        ImageValidator.exactlyOneOf(
                new ImageValidator(sizeValidator),
                new ImageValidator(typeValidator),
                dimensionsValidator);
```

See also `ImageValidator.exactlyOneOf(a, b)` for two arguments.

<br>

Combining general and specific configurations:
```java
// General configuration
var generalValidator = ImageConfig.load(generalInput);

// More specific configuration
var specificValidator = ImageConfig.load(specificInput);

// Produce a less permissive configuration covering both policies
// strictIntersect and laxIntersect are also available as aliases
Optional<ImageValidator> narrowerValidator =
        ImageValidator.intersect(true, generalValidator, specificValidator);

// Produce a more permissive configuration covering both policies
Optional<ImageValidator> broaderValidator =
        ImageValidator.relax(true, generalValidator, specificValidator);
```

Combining configurations with custom strategies:
```java
// Strictly combine
// file size policies using exactlyOne,
// file type policies using union,
// and image dimension policies using a guarded intersect
Optional<ImageValidator> finalValidator = ImageValidator.combine(
        true,
        FileSize::exactlyOne,
        FileType::union,
        FileCombinators.guard(
                ImageDimensions::sameBytesPerUnitEstimate,
                ImageDimensions::intersect),
        a,
        b);
```

Configuration can also be loaded directly from Properties.

An optional logger can be provided to report missing properties.

Alternatively, validation objects may be constructed and combined directly.

See the source code for details. It's extremely well documented.

## Requirements

* Java 27+

## License

See the [MIT License](LICENSE).
