package io.airlift.http.server;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import io.airlift.discovery.client.AnnouncementHttpServerInfo;

import java.lang.annotation.Annotation;
import java.util.Optional;

import static io.airlift.http.server.BinderUtils.qualifiedKey;
import static java.util.Objects.requireNonNull;

public class AnnouncementHttpServerInfoProvider
        implements Provider<AnnouncementHttpServerInfo>
{
    private final Optional<Class<? extends Annotation>> qualifier;
    private Injector injector;

    public AnnouncementHttpServerInfoProvider(Optional<Class<? extends Annotation>> qualifier)
    {
        this.qualifier = requireNonNull(qualifier, "qualifier is null");
    }

    @Inject
    public void setInjector(Injector injector)
    {
        this.injector = requireNonNull(injector, "injector is null");
    }

    @Override
    public AnnouncementHttpServerInfo get()
    {
        return new LocalAnnouncementHttpServerInfo(injector.getInstance(qualifiedKey(qualifier, HttpServerInfo.class)));
    }
}
