package io.airlift.http.server;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import io.airlift.node.NodeInfo;

import java.lang.annotation.Annotation;
import java.util.Optional;

import static io.airlift.http.server.BinderUtils.qualifiedKey;
import static java.util.Objects.requireNonNull;

public class HttpServerInfoProvider
        implements Provider<HttpServerInfo>
{
    private final Optional<Class<? extends Annotation>> qualifier;
    private Injector injector;

    public HttpServerInfoProvider(Optional<Class<? extends Annotation>> qualifier)
    {
        this.qualifier = requireNonNull(qualifier, "qualifier is null");
    }

    @Inject
    public void setInjector(Injector injector)
    {
        this.injector = requireNonNull(injector, "injector is null");
    }

    @Override
    public HttpServerInfo get()
    {
        HttpServerConfig serverConfig = injector.getInstance(qualifiedKey(qualifier, HttpServerConfig.class));
        Optional<HttpConfig> httpConfig = injector.getInstance(qualifiedKey(qualifier, new TypeLiteral<>() {}));
        Optional<HttpsConfig> httpsConfig = injector.getInstance(qualifiedKey(qualifier, new TypeLiteral<>() {}));
        NodeInfo nodeInfo = injector.getInstance(NodeInfo.class);
        return new HttpServerInfo(serverConfig, httpConfig, httpsConfig, nodeInfo);
    }
}
