package io.airlift.http.server;

import com.google.common.collect.ImmutableList;
import com.google.inject.Binder;
import com.google.inject.Key;
import org.eclipse.jetty.util.VirtualThreads;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;
import static java.util.Objects.requireNonNull;

public class HttpServerBinder
{
    private final Binder binder;

    private HttpServerBinder(Binder binder)
    {
        this.binder = requireNonNull(binder, "binder is null").skipSources(getClass());
    }

    public static HttpServerBinder httpServerBinder(Binder binder)
    {
        return new HttpServerBinder(binder);
    }

    public HttpResourceBinding bindResource(String baseUri, String classPathResourceBase)
    {
        return bindResource(baseUri, classPathResourceBase, TheServlet.class);
    }

    public HttpServerBinder enableVirtualThreads()
    {
        if (!VirtualThreads.areSupported()) {
            binder.addError("Virtual threads are not supported");
        }
        newOptionalBinder(binder, Key.get(Boolean.class, EnableVirtualThreads.class)).setBinding().toInstance(true);
        return this;
    }

    /**
     * @deprecated this will be removed in the near future and is only intended as a stopgap
     */
    @Deprecated(forRemoval = true)
    public HttpServerBinder enableLegacyUriCompliance()
    {
        newOptionalBinder(binder, Key.get(Boolean.class, EnableLegacyUriCompliance.class)).setBinding().toInstance(true);
        return this;
    }

    /**
     * @deprecated this will be removed in the near future and is only intended as a stopgap
     */
    @Deprecated(forRemoval = true)
    public HttpServerBinder disableHttp2()
    {
        newOptionalBinder(binder, Key.get(Boolean.class, EnableHttp2.class)).setBinding().toInstance(false);
        return this;
    }

    public void withFeatures(HttpServerFeatures serverFeatures)
    {
        if (serverFeatures.virtualThreads()) {
            enableVirtualThreads();
        }

        if (serverFeatures.legacyUriCompliance()) {
            enableLegacyUriCompliance();
        }

        if (!serverFeatures.http2()) {
            disableHttp2();
        }
    }

    private HttpResourceBinding bindResource(String baseUri,
            String classPathResourceBase,
            Class<? extends Annotation> annotationType)
    {
        HttpResourceBinding httpResourceBinding = new HttpResourceBinding(baseUri, classPathResourceBase);
        newSetBinder(binder, HttpResourceBinding.class, annotationType).addBinding().toInstance(httpResourceBinding);
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
}
