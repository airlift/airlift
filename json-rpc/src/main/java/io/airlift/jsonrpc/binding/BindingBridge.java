package io.airlift.jsonrpc.binding;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import org.glassfish.jersey.internal.inject.AbstractBinder;

import static java.util.Objects.requireNonNull;

class BindingBridge
        extends AbstractBinder
{
    private final RpcMetadata metadata;
    private final Injector injector;

    @Inject
    BindingBridge(RpcMetadata metadata, Injector injector)
    {
        this.metadata = requireNonNull(metadata, "metadata is null");
        this.injector = requireNonNull(injector, "injector is null");
    }

    @Override
    protected void configure()
    {
        metadata.methodMap()
                .values()
                .stream()
                .map(RpcMetadata.MethodMetadata::clazz)
                .forEach(clazz -> bind(injector.getInstance(clazz)).to(clazz).in(Singleton.class));
    }
}
