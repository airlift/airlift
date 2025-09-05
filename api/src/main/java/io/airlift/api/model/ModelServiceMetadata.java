package io.airlift.api.model;

import com.google.common.collect.ImmutableList;
import io.airlift.api.ApiService;
import io.airlift.api.ApiServiceType;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Objects.requireNonNull;

public record ModelServiceMetadata(String name, ModelServiceType type, String description, List<URI> documentationLinks)
{
    public ModelServiceMetadata
    {
        requireNonNull(name, "name is null");
        requireNonNull(type, "type is null");
        requireNonNull(description, "description is null");
        documentationLinks = ImmutableList.copyOf(documentationLinks);
    }

    public static ModelServiceMetadata map(ApiService apiService)
    {
        ApiServiceType apiServiceType;
        try {
            apiServiceType = apiService.type().getConstructor().newInstance();
        }
        catch (Exception e) {
            throw new RuntimeException("Could not create ApiServiceType instance. The class must have a public no-arg constructor. " + apiService.type(), e);
        }

        return new ModelServiceMetadata(
                apiService.name(),
                ModelServiceType.map(apiServiceType),
                apiService.description(),
                Arrays.stream(apiService.documentationLinks())
                        .map(URI::create)
                        .collect(toImmutableList()));
    }

    public String generateDescriptionWithDocumentation()
    {
        StringBuilder builder = new StringBuilder();
        builder.append(description);
        if (!documentationLinks.isEmpty()) {
            builder.append("</br></br>");
            builder.append("See feature documentation:");
            documentationLinks.forEach(docsLink -> builder.append("</br>").append("<a href=\"%s\">Documentation link</a>".formatted(docsLink.toString())));
        }
        return builder.toString();
    }
}
