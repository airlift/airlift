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
package io.airlift.log;

import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import org.weakref.jmx.MBeanExporter;

import javax.annotation.PreDestroy;

import static java.util.Objects.requireNonNull;
import static org.weakref.jmx.guice.ExportBinder.newExporter;

public class LogJmxModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.disableCircularProxies();

        binder.bind(LoggingMBean.class).in(Scopes.SINGLETON);
        newExporter(binder).export(LoggingMBean.class).as("io.airlift.log:name=Logging");
        binder.bind(LogExporter.class).asEagerSingleton();
    }

    @Provides
    @Singleton
    public Logging getLogging()
    {
        return Logging.initialize();
    }

    public static class LogExporter
    {
        private final Logging logging;

        @Inject
        public LogExporter(Logging logging, MBeanExporter exporter)
        {
            this.logging = requireNonNull(logging, "logging is null");
            logging.exportMBeans(requireNonNull(exporter, "exporter is null"));
        }

        @PreDestroy
        public void destroy()
        {
            logging.unexportMBeans();
        }
    }
}
