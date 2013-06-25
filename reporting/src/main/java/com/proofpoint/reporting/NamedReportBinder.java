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
import com.google.inject.name.Named;
import org.weakref.jmx.guice.ExportBinder;

import static org.weakref.jmx.ObjectNames.generatedNameOf;

public class NamedReportBinder
{
    protected final Multibinder<Mapping> binder;
    protected final ExportBinder exportBinder;
    protected final Key<?> key;

    NamedReportBinder(Multibinder<Mapping> binder, ExportBinder exportBinder, Key<?> key)
    {
        this.binder = binder;
        this.exportBinder = exportBinder;
        this.key = key;
    }

    /**
     * Names the metric according to {@link org.weakref.jmx.ObjectNames} name generator methods.
     */
    public void withGeneratedName()
    {
        if (key.getAnnotation() != null) {
            if (key.getAnnotation() instanceof Named) {
                as(generatedNameOf(key.getTypeLiteral().getRawType(), (Named) key.getAnnotation()));
            }
            else {
                as(generatedNameOf(key.getTypeLiteral().getRawType(), key.getAnnotation()));
            }
        }
        else if (key.getAnnotationType() != null) {
            as(generatedNameOf(key.getTypeLiteral().getRawType(), key.getAnnotationType()));
        }
        else {
            as(generatedNameOf(key.getTypeLiteral().getRawType()));
        }
    }

    public void as(String name)
    {
        binder.addBinding().toInstance(new Mapping(name, key));
        exportBinder.export(key).as(name);
    }
}
