/*
 * Copyright 2013 Proofpoint, Inc.
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
package com.proofpoint.reporting;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import org.weakref.jmx.guice.ExportBinder;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static org.weakref.jmx.guice.ExportBinder.newExporter;

public class ReportBinder
{
    private final Binder binder;
    private final Multibinder<Mapping> multibinder;
    private final ExportBinder exportBinder;

    private ReportBinder(Binder binder)
    {
        this.binder = checkNotNull(binder, "binder is null");
        this.multibinder = newSetBinder(binder, Mapping.class);
        exportBinder = newExporter(binder);
    }

    public static ReportBinder reportBinder(Binder binder) {
        return new ReportBinder(binder);
    }

    public AnnotatedReportBinder export(Class<?> clazz)
    {
        return new AnnotatedReportBinder(multibinder, exportBinder, Key.get(clazz));
    }

    public NamedReportBinder export(Key<?> key)
    {
        return new NamedReportBinder(multibinder, exportBinder, key);
    }

    // todo: map and set

    public <T> void bindReportCollection(Class<T> iface) {
        binder.bind(iface).toProvider(new ReportCollectionProvider<>(iface)).in(Scopes.SINGLETON);
    }
}
