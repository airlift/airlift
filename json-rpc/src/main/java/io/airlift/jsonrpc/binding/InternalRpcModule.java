package io.airlift.jsonrpc.binding;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.OptionalBinder;
import io.airlift.jaxrs.JaxrsBinder;
import io.airlift.jsonrpc.JsonRpcMethod;
import io.airlift.jsonrpc.JsonRpcModule;
import io.airlift.jsonrpc.JsonRpcRequestFilter;
import io.airlift.jsonrpc.binding.RpcMetadata.MethodMetadata;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;
import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;
import static java.util.Objects.requireNonNull;

public class InternalRpcModule
        implements Module, JsonRpcModule
{
    private final String basePath;
    private final Set<JsonRpcMethod> methods;
    private final Optional<Consumer<LinkedBindingBuilder<JsonRpcRequestFilter>>> requestFilterBinding;

    private InternalRpcModule(String basePath, Set<JsonRpcMethod> methods, Optional<Consumer<LinkedBindingBuilder<JsonRpcRequestFilter>>> requestFilterBinding)
    {
        this.basePath = requireNonNull(basePath, "basePath is null");
        this.methods = ImmutableSet.copyOf(methods);
        this.requestFilterBinding = requireNonNull(requestFilterBinding, "requestFilterBinding is null");
    }

    @Override
    public void configure(Binder binder)
    {
        binder.bind(InternalRpcHelper.class).in(SINGLETON);

        Map<String, MethodMetadata> methodMap = new HashMap<>();
        RpcResourceBuilder resourceBuilder = new RpcResourceBuilder(basePath, methodMap);

        methods.forEach(method -> {
            binder.bind(method.clazz()).in(SINGLETON);
            resourceBuilder.add(method.clazz(), method.javaMethod(), method.rpcMethod(), method.httpMethod());
        });

        JaxrsBinder jaxrsBinder = jaxrsBinder(binder);
        jaxrsBinder.bind(InternalRpcFilter.class);
        jaxrsBinder.bindInstance(resourceBuilder.build());
        jaxrsBinder.bind(BindingBridge.class);

        binder.bind(RpcMetadata.class).toInstance(new RpcMetadata(basePath, methodMap));

        OptionalBinder<JsonRpcRequestFilter> requestFilterBinder = newOptionalBinder(binder, JsonRpcRequestFilter.class);
        requestFilterBinding.ifPresent(binding -> binding.accept(requestFilterBinder.setBinding()));
    }

    public static Builder<?> builder()
    {
        return new InternalBuilder();
    }

    private static class InternalBuilder
            implements Builder<InternalBuilder>
    {
        private final ImmutableSet.Builder<JsonRpcMethod> methods = ImmutableSet.builder();
        private Optional<Consumer<LinkedBindingBuilder<JsonRpcRequestFilter>>> requestFilterBinding = Optional.empty();
        private String basePath = "jsonrpc";

        @Override
        public InternalBuilder withBasePath(String basePath)
        {
            this.basePath = requireNonNull(basePath, "basePath is null");
            return this;
        }

        @Override
        public InternalBuilder add(JsonRpcMethod jsonRpcMethod)
        {
            methods.add(jsonRpcMethod);
            return this;
        }

        @Override
        public InternalBuilder withRequestFilter(Consumer<LinkedBindingBuilder<JsonRpcRequestFilter>> binding)
        {
            requestFilterBinding = Optional.of(binding);
            return this;
        }

        @Override
        public Module build()
        {
            return new InternalRpcModule(basePath, methods.build(), requestFilterBinding);
        }
    }
}
