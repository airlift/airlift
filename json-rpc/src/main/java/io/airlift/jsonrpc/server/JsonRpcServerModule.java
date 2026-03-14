package io.airlift.jsonrpc.server;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.binder.AnnotatedBindingBuilder;
import jakarta.servlet.Filter;

import java.util.Optional;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkState;
import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static java.util.Objects.requireNonNull;

public class JsonRpcServerModule
        implements Module
{
    private final Consumer<AnnotatedBindingBuilder<JsonRpcRequestHandler>> handlerBinding;
    private final String uriPath;

    private JsonRpcServerModule(Consumer<AnnotatedBindingBuilder<JsonRpcRequestHandler>> handlerBinding, String uriPath)
    {
        this.handlerBinding = requireNonNull(handlerBinding, "handlerBinding is null");
        this.uriPath = requireNonNull(uriPath, "uriPath is null");
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static class Builder
    {
        private Optional<Consumer<AnnotatedBindingBuilder<JsonRpcRequestHandler>>> handlerBinding = Optional.empty();
        private Optional<String> uriPath = Optional.empty();

        private Builder() {}

        public Builder withHandler(Consumer<AnnotatedBindingBuilder<JsonRpcRequestHandler>> binding)
        {
            checkState(handlerBinding.isEmpty(), "handler is already set");
            this.handlerBinding = Optional.of(binding);
            return this;
        }

        public Builder withUriPath(String uriPath)
        {
            checkState(this.uriPath.isEmpty(), "uriPath is already set");
            this.uriPath = Optional.of(uriPath);
            return this;
        }

        public Module build()
        {
            return new JsonRpcServerModule(
                    handlerBinding.orElseThrow(() -> new IllegalStateException("handler is not set")),
                    uriPath.orElseThrow(() -> new IllegalStateException("uriPath is not set")));
        }
    }

    @Override
    public void configure(Binder binder)
    {
        newSetBinder(binder, Filter.class).addBinding().to(JsonRpcServerFilter.class).in(SINGLETON);

        handlerBinding.accept(binder.bind(JsonRpcRequestHandler.class));
        binder.bind(JsonRpcMetadata.class).toInstance(new JsonRpcMetadata(uriPath));
    }
}
