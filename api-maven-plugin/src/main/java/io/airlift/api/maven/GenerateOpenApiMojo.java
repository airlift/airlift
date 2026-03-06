/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.api.maven;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.ImmutableList;
import io.airlift.api.ApiServiceType;
import io.airlift.api.builders.ApiBuilder;
import io.airlift.api.model.ModelApi;
import io.airlift.api.model.ModelServiceType;
import io.airlift.api.openapi.OpenApiMetadata;
import io.airlift.api.openapi.OpenApiMetadata.SecurityScheme;
import io.airlift.api.openapi.OpenApiProvider;
import io.airlift.api.openapi.models.OpenAPI;
import io.airlift.json.JsonMapperProvider;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static io.airlift.api.builders.ApiBuilder.apiBuilder;
import static io.airlift.api.maven.ServiceClassScanner.createClassLoader;

@Mojo(
        name = "generate-openapi",
        defaultPhase = LifecyclePhase.PROCESS_CLASSES,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
        threadSafe = true)
public class GenerateOpenApiMojo
        extends AbstractMojo
{
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "api.serviceClasses")
    private List<String> serviceClasses;

    @Parameter(property = "api.scanPackages")
    private List<String> scanPackages;

    @Parameter(property = "api.serviceTypeClass", defaultValue = "io.airlift.api.maven.DefaultServiceType")
    private String serviceTypeClass;

    @Parameter(property = "api.outputFile", defaultValue = "${project.build.directory}/openapi/openapi.json")
    private File outputFile;

    @Parameter(property = "api.basePath", defaultValue = "/")
    private String basePath;

    @Parameter(property = "api.securityScheme")
    private String securityScheme;

    @Parameter(property = "api.prettyPrint", defaultValue = "true")
    private boolean prettyPrint;

    @Parameter(property = "api.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute()
            throws MojoExecutionException, MojoFailureException
    {
        if (skip) {
            getLog().info("Skipping OpenAPI generation");
            return;
        }

        try (URLClassLoader classLoader = createProjectClassLoader()) {
            validateConfiguration();
            List<Class<?>> serviceClassList = loadServiceClasses(classLoader);

            if (serviceClassList.isEmpty()) {
                getLog().warn("No service classes found. Skipping OpenAPI generation.");
                return;
            }

            getLog().info("Found " + serviceClassList.size() + " service class(es)");

            ApiServiceType serviceType = loadServiceType(classLoader);
            getLog().info("Using service type: " + serviceType.getClass().getName());

            String openApiJson = generateOpenApi(serviceClassList, serviceType);

            writeOutput(openApiJson);
            getLog().info("OpenAPI specification written to: " + outputFile.getAbsolutePath());
        }
        catch (MojoExecutionException | MojoFailureException e) {
            throw e;
        }
        catch (Exception e) {
            throw new MojoExecutionException("Failed to generate OpenAPI specification", e);
        }
    }

    private void validateConfiguration()
            throws MojoFailureException
    {
        boolean hasServiceClasses = serviceClasses != null && !serviceClasses.isEmpty();
        boolean hasScanPackages = scanPackages != null && !scanPackages.isEmpty();

        if (!hasServiceClasses && !hasScanPackages) {
            throw new MojoFailureException("Either 'serviceClasses' or 'scanPackages' must be specified");
        }

        if (hasServiceClasses) {
            serviceClasses = validateNonBlankEntries(serviceClasses, "serviceClasses");
        }

        if (hasScanPackages) {
            scanPackages = validateNonBlankEntries(scanPackages, "scanPackages");
        }
    }

    private List<String> validateNonBlankEntries(List<String> values, String parameterName)
            throws MojoFailureException
    {
        ImmutableList.Builder<String> normalizedValues = ImmutableList.builder();
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            if (value == null || value.trim().isEmpty()) {
                throw new MojoFailureException("Configuration parameter '%s' contains a blank entry at index %d"
                        .formatted(parameterName, i));
            }
            normalizedValues.add(value.trim());
        }
        return normalizedValues.build();
    }

    private URLClassLoader createProjectClassLoader()
            throws MojoExecutionException
    {
        try {
            ImmutableList.Builder<URL> urls = ImmutableList.builder();
            urls.add(new File(project.getBuild().getOutputDirectory()).toURI().toURL());

            for (String element : project.getCompileClasspathElements()) {
                urls.add(new File(element).toURI().toURL());
            }
            for (String element : project.getRuntimeClasspathElements()) {
                urls.add(new File(element).toURI().toURL());
            }

            List<URL> classPathElements = urls.build();
            if (getLog().isDebugEnabled()) {
                getLog().debug("Classpath elements: " + classPathElements);
            }

            return createClassLoader(classPathElements, getClass().getClassLoader());
        }
        catch (MalformedURLException | DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("Failed to create project classloader", e);
        }
    }

    private List<Class<?>> loadServiceClasses(ClassLoader classLoader)
            throws MojoExecutionException
    {
        ImmutableList.Builder<Class<?>> classes = ImmutableList.builder();

        if (serviceClasses != null && !serviceClasses.isEmpty()) {
            for (String className : serviceClasses) {
                try {
                    Class<?> clazz = classLoader.loadClass(className);
                    classes.add(clazz);
                    getLog().debug("Loaded service class: " + className);
                }
                catch (ClassNotFoundException e) {
                    throw new MojoExecutionException("Service class not found: " + className, e);
                }
            }
        }

        if (scanPackages != null && !scanPackages.isEmpty()) {
            getLog().info("Scanning packages: " + scanPackages);
            ServiceClassScanner scanner = new ServiceClassScanner(classLoader, scanPackages);
            List<Class<?>> scannedClasses = scanner.scan();
            classes.addAll(scannedClasses);
            getLog().debug("Found " + scannedClasses.size() + " classes via scanning");
        }

        return classes.build();
    }

    private ApiServiceType loadServiceType(ClassLoader classLoader)
            throws MojoExecutionException
    {
        try {
            Class<?> loadedClass = classLoader.loadClass(serviceTypeClass);
            if (!ApiServiceType.class.isAssignableFrom(loadedClass)) {
                throw new MojoExecutionException("Service type class must implement ApiServiceType: " + serviceTypeClass);
            }
            Class<? extends ApiServiceType> serviceType = loadedClass.asSubclass(ApiServiceType.class);
            return serviceType.getDeclaredConstructor().newInstance();
        }
        catch (ReflectiveOperationException e) {
            throw new MojoExecutionException(
                    "Failed to instantiate service type class: " + serviceTypeClass, e);
        }
    }

    private String generateOpenApi(List<Class<?>> serviceClassList, ApiServiceType serviceType)
            throws MojoExecutionException
    {
        ApiBuilder apiBuilder = apiBuilder();
        for (Class<?> serviceClass : serviceClassList) {
            apiBuilder.add(serviceClass);
        }
        ModelApi modelApi = apiBuilder.build();

        if (!modelApi.modelServices().errors().isEmpty()) {
            for (String error : modelApi.modelServices().errors()) {
                getLog().error("API validation error: " + error);
            }
            throw new MojoExecutionException("API validation failed with %s error(s)".formatted(modelApi.modelServices().errors().size()));
        }

        Optional<SecurityScheme> security = parseSecurityScheme();
        OpenApiMetadata metadata = new OpenApiMetadata(security, ImmutableList.of(), basePath, Duration.ofMinutes(5));

        OpenApiProvider openApiProvider = OpenApiProvider.create(modelApi.modelServices(), metadata);
        ModelServiceType modelServiceType = ModelServiceType.map(serviceType);
        OpenAPI openAPI = openApiProvider.build(modelServiceType, _ -> true);

        JsonMapper jsonMapper = new JsonMapperProvider().get();

        try {
            if (prettyPrint) {
                return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(openAPI);
            }
            return jsonMapper.writeValueAsString(openAPI);
        }
        catch (IOException e) {
            throw new MojoExecutionException("Failed to serialize OpenAPI spec to JSON", e);
        }
    }

    private Optional<SecurityScheme> parseSecurityScheme()
            throws MojoExecutionException
    {
        if (securityScheme == null || securityScheme.isBlank()) {
            return Optional.empty();
        }

        String configuredSecurityScheme = securityScheme.trim();
        try {
            return Optional.of(SecurityScheme.valueOf(configuredSecurityScheme));
        }
        catch (IllegalArgumentException e) {
            throw new MojoExecutionException(
                    "Invalid security scheme: %s. Valid values are: %s".formatted(configuredSecurityScheme, Arrays.toString(SecurityScheme.values())));
        }
    }

    private void writeOutput(String content)
            throws MojoExecutionException
    {
        try {
            Path outputPath = outputFile.toPath();
            if (!outputPath.isAbsolute()) {
                outputPath = project.getBasedir().toPath().resolve(outputPath).normalize();
            }
            Path parent = outputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(outputPath, content);
        }
        catch (IOException e) {
            throw new MojoExecutionException("Failed to write output file: " + outputFile, e);
        }
    }
}
