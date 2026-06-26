/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.http.server.testing;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Scopes;
import io.airlift.configuration.AbstractConfigurationAwareModule;
import io.airlift.discovery.client.AnnouncementHttpServerInfo;
import io.airlift.http.server.AnnouncementHttpServerInfoProvider;
import io.airlift.http.server.HttpConfig;
import io.airlift.http.server.HttpServer;
import io.airlift.http.server.HttpServer.ClientCertificate;
import io.airlift.http.server.HttpServerBinder.HttpResourceBinding;
import io.airlift.http.server.HttpServerConfig;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.http.server.HttpServerInfoProvider;
import io.airlift.http.server.HttpsConfig;
import io.airlift.http.server.LocalAnnouncementHttpServerInfo;
import io.airlift.http.server.ServerFeature;
import jakarta.annotation.Nullable;
import jakarta.servlet.Filter;

import java.lang.annotation.Annotation;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;
import static io.airlift.configuration.ConfigBinder.configBinder;
import static java.util.Objects.requireNonNull;

public class TestingHttpServerModule
        extends AbstractConfigurationAwareModule
{
    private final String name;
    private final int httpPort;
    private final Optional<Class<? extends Annotation>> qualifier;
    private final @Nullable String configPrefix;

    public TestingHttpServerModule(String name)
    {
        this(name, 0);
    }

    public TestingHttpServerModule(String name, int httpPort)
    {
        this(name, httpPort, Optional.empty(), null);
    }

    public TestingHttpServerModule(String name, Class<? extends Annotation> qualifier, String configPrefix)
    {
        this(name, 0, qualifier, configPrefix);
    }

    public TestingHttpServerModule(String name, int httpPort, Class<? extends Annotation> qualifier, String configPrefix)
    {
        this(name, httpPort, Optional.of(requireNonNull(qualifier, "qualifier is null")), requireNonNull(configPrefix, "configPrefix is null"));
    }

    private TestingHttpServerModule(String name, int httpPort, Optional<Class<? extends Annotation>> qualifier, @Nullable String configPrefix)
    {
        this.name = requireNonNull(name, "name is null");
        this.httpPort = httpPort;
        this.qualifier = requireNonNull(qualifier, "qualifier is null");
        this.configPrefix = configPrefix;

        if (this.qualifier.isPresent()) {
            checkArgument(this.configPrefix != null, "qualifier is present but configPrefix is null");
        }
    }

    @Override
    protected void setup(Binder binder)
    {
        configBinder(binder).bindConfig(qualifiedKey(HttpServerConfig.class), HttpServerConfig.class, configPrefix);

        if (qualifier.isPresent()) {
            binder.bind(qualifiedKey(HttpServerInfo.class))
                    .toProvider(new HttpServerInfoProvider(qualifier))
                    .in(Scopes.SINGLETON);
            binder.bind(qualifiedKey(TestingHttpServer.class))
                    .toProvider(new TestingHttpServerProvider(name, qualifier))
                    .in(Scopes.SINGLETON);
            binder.bind(qualifiedKey(HttpServer.class)).to(qualifiedKey(TestingHttpServer.class));
            binder.bind(qualifiedKey(AnnouncementHttpServerInfo.class))
                    .toProvider(new AnnouncementHttpServerInfoProvider(qualifier))
                    .in(Scopes.SINGLETON);
        }
        else {
            binder.bind(String.class).annotatedWith(ForTestingServer.class).toInstance(name);
            binder.bind(HttpServerInfo.class).in(Scopes.SINGLETON);
            binder.bind(TestingHttpServer.class).in(Scopes.SINGLETON);
            binder.bind(HttpServer.class).to(Key.get(TestingHttpServer.class));
            binder.bind(AnnouncementHttpServerInfo.class).to(LocalAnnouncementHttpServerInfo.class);
        }

        newOptionalBinder(binder, qualifiedKey(ClientCertificate.class)).setDefault().toInstance(ClientCertificate.NONE);
        newSetBinder(binder, qualifiedKey(ServerFeature.class));
        newSetBinder(binder, qualifiedKey(Filter.class));
        newSetBinder(binder, qualifiedKey(HttpResourceBinding.class));

        newOptionalBinder(binder, qualifiedKey(HttpConfig.class));
        newOptionalBinder(binder, qualifiedKey(HttpsConfig.class));
        HttpServerConfig config = buildConfigObject(qualifiedKey(HttpServerConfig.class), HttpServerConfig.class, configPrefix);
        if (config.isHttpEnabled()) {
            configBinder(binder).bindConfig(qualifiedKey(HttpConfig.class), HttpConfig.class, configPrefix);
            configBinder(binder).bindConfigDefaults(qualifiedKey(HttpConfig.class), httpConfig -> httpConfig
                    .setHttpPort(httpPort));
        }
        if (config.isHttpsEnabled()) {
            configBinder(binder).bindConfig(qualifiedKey(HttpsConfig.class), HttpsConfig.class, configPrefix);
            configBinder(binder).bindConfigDefaults(qualifiedKey(HttpsConfig.class), httpsConfig -> {
                if (httpPort == 0) {
                    httpsConfig.setHttpsPort(0);
                }
            });
        }
    }

    private <T> Key<T> qualifiedKey(Class<T> type)
    {
        return qualifier
                .map(annotation -> Key.get(type, annotation))
                .orElseGet(() -> Key.get(type));
    }
}
