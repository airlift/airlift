package io.airlift.configuration.validation;

import jakarta.validation.ConstraintDeclarationException;
import jakarta.validation.constraints.NotNull;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static io.airlift.testing.ValidationAssertions.assertFailsValidation;
import static io.airlift.testing.ValidationAssertions.assertValidates;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestFileExistsValidator
{
    @Test
    public void testFileExistsValidator()
    {
        Path existingPath = Paths.get("./pom.xml");

        assertValidates(existingPath);
        assertValidates(existingPath.toFile());
        assertValidates(existingPath.toString());
    }

    @Test
    public void testFileExistsNullValue()
    {
        assertFailsValidation(new TestedBean(null), "testedValue", "must not be null", NotNull.class);
    }

    @Test
    public void testFileDoesNotExist()
    {
        assertFailsValidation(new TestedBean(Paths.get("./file-not-exist.xml")), "testedValue", "file does not exist: ./file-not-exist.xml", FileExists.class);
        assertFailsValidation(new TestedBean(Paths.get("./some-name.xml").toFile()), "testedValue", "file does not exist: ./some-name.xml", FileExists.class);
        assertFailsValidation(new TestedBean("./some-other-name.xml"), "testedValue", "file does not exist: ./some-other-name.xml", FileExists.class);
    }

    @Test
    public void testInvalidType()
    {
        assertThatThrownBy(() -> assertValidates(new TestedBean(new BigDecimal(100))))
                .isInstanceOf(ConstraintDeclarationException.class)
                .hasMessageContaining("Unsupported type for @FileExists: java.math.BigDecimal");
    }

    @Test
    public void testInvalidAnnotationUse()
    {
        assertThatThrownBy(() -> assertValidates(new InvalidBean()))
                .isInstanceOf(ConstraintDeclarationException.class)
                .hasMessageContaining("FileExists.message cannot be specified");
    }

    @Test
    public void testTypeTargetAnnotationUse()
    {
        assertValidates(new OptionalValueBean(Optional.of(Paths.get("./pom.xml"))));
        assertValidates(new OptionalValueBean(Optional.empty()));

        assertFailsValidation(
                new OptionalValueBean(Optional.of(Paths.get("./not-existing.xml"))),
                "value",
                "file does not exist: ./not-existing.xml",
                FileExists.class);
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
