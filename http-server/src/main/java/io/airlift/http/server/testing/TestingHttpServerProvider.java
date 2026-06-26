package io.airlift.http.server.testing;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import io.airlift.http.server.HttpConfig;
import io.airlift.http.server.HttpServer.ClientCertificate;
import io.airlift.http.server.HttpServerBinder.HttpResourceBinding;
import io.airlift.http.server.HttpServerConfig;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.http.server.HttpsConfig;
import io.airlift.http.server.ServerFeature;
import io.airlift.node.NodeInfo;
import jakarta.servlet.Filter;
import jakarta.servlet.Servlet;

import java.lang.annotation.Annotation;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Throwables.throwIfUnchecked;
import static java.util.Objects.requireNonNull;

class TestingHttpServerProvider
        implements Provider<TestingHttpServer>
{
    private final String name;
    private final Optional<Class<? extends Annotation>> qualifier;

    private Injector injector;

    TestingHttpServerProvider(String name, Optional<Class<? extends Annotation>> qualifier)
    {
        this.name = requireNonNull(name, "name is null");
        this.qualifier = requireNonNull(qualifier, "qualifier is null");
    }

    @Inject
    public void setInjector(Injector injector)
    {
        this.injector = requireNonNull(injector, "injector is null");
    }

    @Override
    public TestingHttpServer get()
    {
        try {
            return new TestingHttpServer(
                    name,
                    injector.getInstance(qualifiedKey(HttpServerInfo.class)),
                    injector.getInstance(NodeInfo.class),
                    injector.getInstance(qualifiedKey(HttpServerConfig.class)),
                    injector.getInstance(qualifiedKey(new TypeLiteral<Optional<HttpConfig>>() {})),
                    injector.getInstance(qualifiedKey(new TypeLiteral<Optional<HttpsConfig>>() {})),
                    injector.getInstance(qualifiedKey(Servlet.class)),
                    injector.getInstance(qualifiedKey(new TypeLiteral<Set<Filter>>() {})),
                    injector.getInstance(qualifiedKey(new TypeLiteral<Set<HttpResourceBinding>>() {})),
                    injector.getInstance(qualifiedKey(new TypeLiteral<Set<ServerFeature>>() {})),
                    injector.getInstance(qualifiedKey(ClientCertificate.class)));
        }
        catch (Exception e) {
            throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }

    private <T> Key<T> qualifiedKey(Class<T> type)
    {
        return qualifier
                .map(annotation -> Key.get(type, annotation))
                .orElseGet(() -> Key.get(type));
    }

    private <T> Key<T> qualifiedKey(TypeLiteral<T> type)
    {
        return qualifier
                .map(annotation -> Key.get(type, annotation))
                .orElseGet(() -> Key.get(type));
    }
}
