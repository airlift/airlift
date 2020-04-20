package io.airlift.configuration.validation;

import org.apache.bval.jsr.ApacheValidationProvider;
import org.testng.annotations.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.ValidationException;
import javax.validation.Validator;
import javax.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestFileExistsValidator
{
    private static final Validator VALIDATOR = Validation.byProvider(ApacheValidationProvider.class).configure().buildValidatorFactory().getValidator();

    @Test
    public void testFileExistsValidator()
    {
        Path existingPath = Paths.get("./pom.xml");

        assertValidValue(existingPath);
        assertValidValue(existingPath.toFile());
        assertValidValue(existingPath.toString());
    }

    @Test
    public void testFileExistsNullValue()
    {
        assertInvalidValue(new TestedBean(null), "testedValue", "may not be null");
    }

    @Test
    public void testFileDoesNotExist()
    {
        assertInvalidValue(new TestedBean(Paths.get("./file-not-exist.xml")), "testedValue", "file does not exist: ./file-not-exist.xml");
        assertInvalidValue(new TestedBean(Paths.get("./some-name.xml").toFile()), "testedValue", "file does not exist: ./some-name.xml");
        assertInvalidValue(new TestedBean("./some-other-name.xml"), "testedValue", "file does not exist: ./some-other-name.xml");
    }

    @Test
    public void testInvalidType()
    {
        assertThatThrownBy(() -> VALIDATOR.validate(new TestedBean(new BigDecimal(100))))
            .isInstanceOf(ValidationException.class)
            .hasMessage("java.lang.IllegalArgumentException: Unsupported type for @FileExists: java.math.BigDecimal");
    }

    @Test
    public void testInvalidAnnotationUse()
    {
        assertThatThrownBy(() -> VALIDATOR.validate(new InvalidBean()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("com.google.common.base.VerifyException: FileExists.message cannot be specified");
    }

    @Test
    public void testTypeTargetAnnotationUse()
    {
        assertValidValue(new OptionalValueBean(Optional.of(Paths.get("./pom.xml"))));
        assertValidValue(new OptionalValueBean(Optional.empty()));

        assertInvalidValue(
                new OptionalValueBean(Optional.of(Paths.get("./not-existing.xml"))),
                "value",
                "file does not exist: ./not-existing.xml");
    }

    private static void assertValidValue(Object validatedObject)
    {
        assertThat(VALIDATOR.validate(validatedObject)).isEmpty();
    }

    private static void assertInvalidValue(Object validatedObject, String propertyPath, String message)
    {
        Set<ConstraintViolation<Object>> violations = VALIDATOR.validate(validatedObject);
        assertThat(violations).hasSize(1);

        ConstraintViolation<Object> violation = violations.iterator().next();
        assertThat(violation.getMessage()).isEqualTo(message);
        assertThat(violation.getPropertyPath().toString()).isEqualTo(propertyPath);
    }

    private static final class TestedBean
    {
        private final Object testedValue;

        public TestedBean(Object testedValue)
        {
            // Could be null
            this.testedValue = testedValue;
        }

        @FileExists
        @NotNull
        public Object getTestedValue()
        {
            return testedValue;
        }
    }

    private static final class InvalidBean
    {
        @FileExists(message = "Message should not be allowed to be set")
        public Path getValue()
        {
            return Paths.get("./pom.xml");
        }
    }

    private static final class OptionalValueBean
    {
        private final Optional<Path> value;

        OptionalValueBean(Optional<Path> value)
        {
            this.value = value;
        }

        public Optional<@FileExists Path> getValue()
        {
            return value;
        }
    }
}
