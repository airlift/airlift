package io.airlift.api.binding;

import com.google.inject.Module;
import io.airlift.api.ApiPolyResource;
import io.airlift.api.ApiResource;
import io.airlift.json.JsonSubType;
import io.airlift.json.JsonSubType.SubTypeSubBuilder;
import io.airlift.json.JsonSubTypeBinder;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static io.airlift.json.JsonSubTypeBinder.jsonSubTypeBinder;
import static java.util.Objects.requireNonNull;

public class PolyResourceModule
{
    private PolyResourceModule() {}

    @SuppressWarnings("UnusedReturnValue")
    public interface Builder
    {
        Builder addPolyResource(Class<?> polyResourceClass);

        Module build();
    }

    public static Builder builder()
    {
        return new Builder()
        {
            private final Set<Metadata> metadatas = new HashSet<>();

            @Override
            public Builder addPolyResource(Class<?> polyResourceClass)
            {
                if (!(polyResourceClass.isInterface() && polyResourceClass.isSealed())) {
                    throw new IllegalArgumentException("Poly resources must be sealed interfaces");
                }

                ApiPolyResource apiPolyResource = polyResourceClass.getAnnotation(ApiPolyResource.class);
                if (apiPolyResource == null) {
                    throw new IllegalArgumentException("Poly resource class must be annotated with %s".formatted(ApiPolyResource.class.getName()));
                }

                Class<?>[] permittedSubclasses = polyResourceClass.getPermittedSubclasses();
                if (permittedSubclasses == null) {
                    throw new IllegalArgumentException("Poly resource class does not have any subclasses");
                }

                metadatas.add(new Metadata(polyResourceClass, apiPolyResource.key()));

                return this;
            }

            @SuppressWarnings({"rawtypes", "unchecked"})
            @Override
            public Module build()
            {
                return binder -> {
                    JsonSubTypeBinder jsonSubTypeBinder = jsonSubTypeBinder(binder);

                    metadatas.forEach(metadata -> {
                        SubTypeSubBuilder subTypeSubBuilder = JsonSubType.builder().forBase(metadata.clazz(), metadata.key());

                        Stream.of(metadata.clazz.getPermittedSubclasses()).forEach(subClass -> {
                            ApiResource subApiResource = subClass.getAnnotation(ApiResource.class);
                            if (subApiResource == null) {
                                throw new IllegalArgumentException("Subclass %s is missing %s annotation".formatted(subClass, ApiResource.class.getName()));
                            }
                            subTypeSubBuilder.add(subClass, subApiResource.name());
                        });

                        jsonSubTypeBinder.bindJsonSubType(subTypeSubBuilder.build());
                    });
                };
            }
        };
    }

    private record Metadata(Class<?> clazz, String key)
    {
        private Metadata
        {
            requireNonNull(clazz, "clazz is null");
            requireNonNull(key, "key is null");
        }
    }
}
