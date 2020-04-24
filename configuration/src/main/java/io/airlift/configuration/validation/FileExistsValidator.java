package io.airlift.configuration.validation;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.google.common.base.Verify.verify;

public class FileExistsValidator
        implements ConstraintValidator<FileExists, Object>
{
    @Override
    public void initialize(FileExists ignored)
    {
        // Annotation has no properties
        verify(ignored.message().isEmpty(), "FileExists.message cannot be specified");
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
        if (path instanceof String) {
            return Files.exists(Paths.get((String) path));
        }

        if (path instanceof Path) {
            return Files.exists((Path) path);
        }

        if (path instanceof File) {
            return ((File) path).exists();
        }

        throw new IllegalArgumentException("Unsupported type for @FileExists: " + path.getClass().getName());
    }
}
