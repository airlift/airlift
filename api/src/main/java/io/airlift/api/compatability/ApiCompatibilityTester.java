package io.airlift.api.compatability;

import io.airlift.api.ApiTrait;
import io.airlift.api.model.ModelServices;
import io.airlift.log.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static io.airlift.api.compatability.ApiCompatibilityUtil.specFileExists;
import static io.airlift.api.compatability.ApiCompatibilityUtil.specFileName;
import static io.airlift.api.compatability.ApiCompatibilityUtil.validateMethodSpec;
import static io.airlift.api.compatability.ApiCompatibilityUtil.writeMethodSpecFile;
import static io.airlift.api.internals.Mappers.MethodPathMode.FOR_DISPLAY;
import static io.airlift.api.internals.Mappers.buildFullPath;
import static io.airlift.api.internals.Mappers.buildMethodPath;
import static io.airlift.api.internals.Mappers.buildServicePath;
import static java.util.Objects.requireNonNull;

public final class ApiCompatibilityTester
{
    private static final Logger log = Logger.get(ApiCompatibilityTester.class);

    private final String sourcePath;
    private final String newFileCreationPropertyName;
    private final String classesDir;
    private final String resourcesDir;

    public static String basePathFromClass(Class<?> clazz)
    {
        File sourcePath = new File(clazz.getProtectionDomain().getCodeSource().getLocation().getPath());
        return sourcePath.getAbsolutePath();
    }

    public static Builder builder(String basePath)
    {
        return new Builder(basePath);
    }

    public static ApiCompatibilityTester newDefaultInstance(String basePath)
    {
        return builder(basePath).build();
    }

    public static final class Builder
    {
        private final String basePath;
        private String testClassesDir = "/target/test-classes";
        private String classesDir = "/target/classes";
        private String resourcesDir = "/src/test/resources/api/compatibility";
        private String newFileCreationPropertyName = "API_COMPATIBILITY_CREATION_ENABLED";

        private Builder(String basePath)
        {
            this.basePath = requireNonNull(basePath, "basePath is null");
        }

        public Builder withTestClassesDir(String testClassesDir)
        {
            this.testClassesDir = requireNonNull(testClassesDir, "testClassesDir is null");
            return this;
        }

        public Builder withClassesDir(String classesDir)
        {
            this.classesDir = requireNonNull(classesDir, "classesDir is null");
            return this;
        }

        public Builder withResourcesDir(String resourcesDir)
        {
            this.resourcesDir = requireNonNull(resourcesDir, "resourcesDir is null");
            return this;
        }

        public Builder withNewFileCreationPropertyName(String newFileCreationPropertyName)
        {
            this.newFileCreationPropertyName = requireNonNull(newFileCreationPropertyName, "newFileCreationPropertyName is null");
            return this;
        }

        public ApiCompatibilityTester build()
        {
            return new ApiCompatibilityTester(this);
        }
    }

    public void test(ModelServices modelServices)
    {
        boolean newFileCreationEnabled = Boolean.getBoolean(newFileCreationPropertyName);

        List<String> errors = new ArrayList<>();
        Collection<String> usedSet = new HashSet<>();
        modelServices.services().forEach(modelService -> {
            String fixedPath = sourcePath.replace(classesDir, (resourcesDir + "/v%s/%s").formatted(modelService.service().type().version(), modelService.service().type().id()));
            File directory = Paths.get(fixedPath).toFile();
            checkArgument(directory.exists() || directory.mkdirs(), "Could not make directory: " + directory);

            modelService.methods()
                    .stream()
                    .filter(modelMethod -> !modelMethod.traits().contains(ApiTrait.BETA))
                    .forEach(modelMethod -> {
                        String hashFileName = specFileName(modelService.service(), modelMethod);
                        usedSet.add(hashFileName);
                        if (specFileExists(directory, modelService.service(), modelMethod)) {
                            try {
                                validateMethodSpec(directory, modelService.service(), modelMethod);
                            }
                            catch (Exception e) {
                                errors.add(e.getMessage());
                            }
                        }
                        else if (newFileCreationEnabled) {
                            errors.add("Created new compatibility file for API: %s method: %s at: %s".formatted(buildServicePath(modelService.service()), buildMethodPath(modelMethod, FOR_DISPLAY), hashFileName));
                            writeMethodSpecFile(directory, modelService.service(), modelMethod);
                        }
                        else {
                            errors.add("New API detected: %s. Add \"-D%s=true\" to the command line to generate compatibility file.".formatted(buildFullPath(modelService.service(), modelMethod, FOR_DISPLAY), newFileCreationPropertyName));
                        }
                    });
        });

        String fixedPath = sourcePath.replace(classesDir, resourcesDir);
        try {
            Files.walk(Paths.get(fixedPath)).forEach(compatibilityPath -> {
                File compatibilityFile = compatibilityPath.toFile();
                if (compatibilityFile.isFile() && !compatibilityFile.isHidden()) {
                    if (!usedSet.contains(compatibilityFile.getName())) {
                        errors.add("Compatibility file without implementation - did the method get removed/changed? %s".formatted(compatibilityFile.getAbsolutePath()));
                    }
                }
            });
        }
        catch (IOException e) {
            errors.add(e.getMessage());
        }

        if (!errors.isEmpty()) {
            errors.forEach(log::error);

            log.error("""
                    
                    
                    !!!! API Compatibility errors found. See log above for details. !!!!
                    
                    If the errors are due to new APIs detected you must run with %s
                    defined via -D%s=true. Run with this defined _two_ times to
                    eliminate this error and ensure that the new compatability files are committed to git.
                    
                    """.formatted(newFileCreationPropertyName, newFileCreationPropertyName));

            System.exit(1);
        }
    }

    private ApiCompatibilityTester(Builder builder)
    {
        File basePath = new File(builder.basePath);

        sourcePath = basePath.getAbsolutePath();
        newFileCreationPropertyName = builder.newFileCreationPropertyName;
        classesDir = sourcePath.contains(builder.testClassesDir) ? builder.testClassesDir : builder.classesDir;
        resourcesDir = builder.resourcesDir;
    }
}
