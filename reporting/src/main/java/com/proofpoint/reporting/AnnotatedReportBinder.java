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

import com.google.inject.Key;
import com.google.inject.multibindings.Multibinder;
import org.weakref.jmx.guice.ExportBinder;

import java.lang.annotation.Annotation;

public class AnnotatedReportBinder
    extends NamedReportBinder
{
    AnnotatedReportBinder(Multibinder<Mapping> binder, ExportBinder exportBinder, Key<?> key)
    {
        super(binder, exportBinder, key);
    }

    public NamedReportBinder annotatedWith(Annotation annotation)
    {
        return new NamedReportBinder(binder, exportBinder, Key.get(key.getTypeLiteral(), annotation));
    }

    public NamedReportBinder annotatedWith(Class<? extends Annotation> annotationClass)
    {
        return new NamedReportBinder(binder, exportBinder, Key.get(key.getTypeLiteral(), annotationClass));
    }
}
