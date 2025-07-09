package io.airlift.http.server;

import com.google.common.collect.ImmutableList;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.multibindings.Multibinder;
import jakarta.annotation.Nullable;
import org.eclipse.jetty.util.VirtualThreads;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static io.airlift.http.server.ServerFeature.CASE_SENSITIVE_HEADER_CACHE;
import static io.airlift.http.server.ServerFeature.LEGACY_URI_COMPLIANCE;
import static io.airlift.http.server.ServerFeature.VIRTUAL_THREADS;
import static java.util.Objects.requireNonNull;

public class HttpServerBinder
{
    private final Binder binder;
    private final Multibinder<ServerFeature> features;
    private final Optional<Class<? extends Annotation>> qualifier;

    private HttpServerBinder(Binder binder, @Nullable Class<? extends Annotation> qualifier)
    {
        requireNonNull(binder, "binder is null");
        this.binder = binder.skipSources(getClass());
        this.qualifier = Optional.ofNullable(qualifier);
        this.features = newSetBinder(binder, BinderUtils.qualifiedKey(this.qualifier, ServerFeature.class));
    }

    public static HttpServerBinder httpServerBinder(Binder binder)
    {
        return new HttpServerBinder(binder, null);
    }

    public static HttpServerBinder httpServerBinder(Binder binder, Class<? extends Annotation> qualifier)
    {
        requireNonNull(qualifier, "qualifier is null");
        return new HttpServerBinder(binder, qualifier);
    }

    @Deprecated // Use withFeature(VIRTUAL_THREADS) instead
    public HttpServerBinder enableVirtualThreads()
    {
        return withFeature(VIRTUAL_THREADS);
    }

    @Deprecated  // Use withFeature(CASE_SENSITIVE_HEADER_CACHE) instead
    public HttpServerBinder enableCaseSensitiveHeaderCache()
    {
        return withFeature(CASE_SENSITIVE_HEADER_CACHE);
    }

    @Deprecated // Use withFeature(LEGACY_URI_COMPLIANCE) instead
    public HttpServerBinder enableLegacyUriCompliance()
    {
        return withFeature(LEGACY_URI_COMPLIANCE);
    }

    public HttpServerBinder withFeature(ServerFeature serverFeature)
    {
        if (serverFeature == VIRTUAL_THREADS && !VirtualThreads.areSupported()) {
            binder.addError("Virtual threads are not supported");
        }
        features.addBinding().toInstance(serverFeature);
        return this;
    }

    public HttpServerBinder withFeatures(Set<ServerFeature> serverFeatures)
    {
        serverFeatures.forEach(this::withFeature);
        return this;
    }

    public HttpResourceBinding bindResource(String baseUri, String classPathResourceBase)
    {
        HttpResourceBinding httpResourceBinding = new HttpResourceBinding(baseUri, classPathResourceBase);
        newSetBinder(binder, qualifiedKey(HttpResourceBinding.class)).addBinding().toInstance(httpResourceBinding);
        return httpResourceBinding;
    }

    public static class HttpResourceBinding
    {
        private final String baseUri;
        private final String classPathResourceBase;
        private final List<String> welcomeFiles = new ArrayList<>();

        public HttpResourceBinding(String baseUri, String classPathResourceBase)
        {
            this.baseUri = baseUri;
            this.classPathResourceBase = classPathResourceBase;
        }

        public String getBaseUri()
        {
            return baseUri;
        }

        public String getClassPathResourceBase()
        {
            return classPathResourceBase;
        }

        public List<String> getWelcomeFiles()
        {
            return ImmutableList.copyOf(welcomeFiles);
        }

        public HttpResourceBinding withWelcomeFile(String welcomeFile)
        {
            welcomeFiles.add(welcomeFile);
            return this;
        }
    }

    private <T> Key<T> qualifiedKey(Class<T> type)
    {
        return BinderUtils.qualifiedKey(qualifier, type);
    }
}
