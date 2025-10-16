/*
 * Copyright 2010 Proofpoint, Inc.
 *
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
package io.airlift.http.server;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import io.airlift.http.server.HttpServer.ClientCertificate;
import io.airlift.node.NodeInfo;
import jakarta.servlet.Servlet;

import javax.management.MBeanServer;

import java.lang.annotation.Annotation;
import java.util.Optional;

import static com.google.common.base.Throwables.throwIfUnchecked;
import static io.airlift.http.server.BinderUtils.qualifiedKey;
import static java.util.Objects.requireNonNull;

/**
 * Provides an instance of a Jetty server ready to be configured with
 * com.google.inject.servlet.ServletModule
 */
public class HttpServerProvider
        implements Provider<HttpServer>
{
    private final String name;
    private final Optional<Class<? extends Annotation>> qualifier;

    private Injector injector;
    private Optional<MBeanServer> mbeanServer = Optional.empty();

    public HttpServerProvider(String name, Optional<Class<? extends Annotation>> qualifier)
    {
        this.name = requireNonNull(name, "name is null");
        this.qualifier = requireNonNull(qualifier, "qualifier is null");
    }

    @Inject(optional = true)
    public void setMBeanServer(MBeanServer mBeanServer)
    {
        mbeanServer = Optional.of(requireNonNull(mBeanServer, "mBeanServer is null"));
    }

    @Inject
    public void setInjector(Injector injector)
    {
        this.injector = requireNonNull(injector, "injector is null");
    }

    @Override
    public HttpServer get()
    {
        try {
            HttpServer httpServer = new HttpServer(
                    name,
                    injector.getInstance(qualifiedKey(qualifier, HttpServerInfo.class)),
                    injector.getInstance(NodeInfo.class),
                    injector.getInstance(qualifiedKey(qualifier, HttpServerConfig.class)),
                    injector.getInstance(qualifiedKey(qualifier, new TypeLiteral<>() {})),
                    injector.getInstance(qualifiedKey(qualifier, Servlet.class)),
                    injector.getInstance(qualifiedKey(qualifier, new TypeLiteral<>() {})),
                    injector.getInstance(qualifiedKey(qualifier, new TypeLiteral<>() {})),
                    injector.getInstance(qualifiedKey(qualifier, new TypeLiteral<>() {})),
                    injector.getInstance(qualifiedKey(qualifier, ClientCertificate.class)),
                    mbeanServer,
                    injector.getInstance(qualifiedKey(qualifier, new TypeLiteral<>() {})));
            httpServer.start();
            return httpServer;
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        catch (Exception e) {
            throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }
}
