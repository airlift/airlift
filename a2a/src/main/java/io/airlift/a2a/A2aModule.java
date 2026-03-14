package io.airlift.a2a;

import com.google.common.base.Preconditions;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.binder.AnnotatedBindingBuilder;
import io.airlift.a2a.internal.AgentCardResource;
import io.airlift.jsonrpc.server.JsonRpcServerModule;

import java.util.Optional;
import java.util.function.Consumer;

import static com.google.inject.Scopes.SINGLETON;
import static io.airlift.a2a.A2AProtocolVersion.V_0_3_0;
import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;
import static java.util.Objects.requireNonNull;

public class A2aModule
        implements Module
{
    private final String uriPath;
    private final Consumer<AnnotatedBindingBuilder<AgentCardSupplier>> agentCardBinding;
    private final A2AProtocolVersion protocolVersion;

    private A2aModule(String uriPath, Consumer<AnnotatedBindingBuilder<AgentCardSupplier>> agentCardBinding, A2AProtocolVersion protocolVersion)
    {
        this.uriPath = requireNonNull(uriPath, "uriPath is null");
        this.agentCardBinding = requireNonNull(agentCardBinding, "agentCardBinding is null");
        this.protocolVersion = requireNonNull(protocolVersion, "protocolVersion is null");
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static class Builder
    {
        private Optional<Consumer<AnnotatedBindingBuilder<AgentCardSupplier>>> agentCardBinding = Optional.empty();
        private String uriPath = "/a2a";
        private A2AProtocolVersion protocolVersion = V_0_3_0;

        private Builder() {}

        public Builder withAgentCardSupplier(Consumer<AnnotatedBindingBuilder<AgentCardSupplier>> binding)
        {
            Preconditions.checkState(agentCardBinding.isEmpty(), "AgentCardSupplier binding is already set");
            this.agentCardBinding = Optional.of(binding);

            return this;
        }

        public Builder withUriPath(String uriPath)
        {
            this.uriPath = requireNonNull(uriPath, "uriPath is null");
            if (!this.uriPath.startsWith("/")) {
                this.uriPath = "/" + this.uriPath;
            }

            return this;
        }

        public Builder withProtocolVersion(A2AProtocolVersion protocolVersion)
        {
            this.protocolVersion = requireNonNull(protocolVersion, "protocolVersion is null");
            return this;
        }

        public Module build()
        {
            return new A2aModule(
                    uriPath,
                    agentCardBinding.orElseThrow(() -> new IllegalStateException("agentCardBinding is empty")),
                    protocolVersion);
        }
    }

    @Override
    public void configure(Binder binder)
    {
        agentCardBinding.accept(binder.bind(AgentCardSupplier.class));

        binder.bind(A2aServer.class).in(SINGLETON);
        Module jsonRpcModule = JsonRpcServerModule.builder()
                .withUriPath(uriPath)
                .withHandler(binding -> binding.to(A2aServer.class).in(SINGLETON))
                .build();
        binder.install(jsonRpcModule);

        jaxrsBinder(binder).bind(AgentCardResource.class);

        binder.bind(A2AProtocolVersion.class).toInstance(protocolVersion);
    }
}
