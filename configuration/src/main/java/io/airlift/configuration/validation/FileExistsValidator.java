package io.airlift.configuration.validation;

import jakarta.validation.ConstraintDeclarationException;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileExistsValidator
        implements ConstraintValidator<FileExists, Object>
{
    @Override
    public void initialize(FileExists ignored)
    {
        // Annotation has no properties
        if (!ignored.message().isEmpty()) {
            throw new ConstraintDeclarationException("FileExists.message cannot be specified");
        }
    }

    @Override
    public boolean isValid(Object path, ConstraintValidatorContext context)
    {
        if (path == null) {
            // @NotNull responsibility
            return true;
        }

        boolean fileExists = exists(path);

        if (!fileExists) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("file does not exist: " + path)
                    .addConstraintViolation();
        }

        return fileExists;
    }

    private static boolean exists(Object path)
    {
        if (path instanceof String stringValue) {
            return Files.exists(Paths.get(stringValue));
        }

        if (path instanceof Path pathValue) {
            return Files.exists(pathValue);
        }

        if (path instanceof File fileValue) {
            return fileValue.exists();
        }

        throw new ConstraintDeclarationException("Unsupported type for @FileExists: " + path.getClass().getName());
    }
}
