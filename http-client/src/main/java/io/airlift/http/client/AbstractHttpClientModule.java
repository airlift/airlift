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
package io.airlift.http.client;

import com.google.inject.Binder;
import com.google.inject.Module;

import java.lang.annotation.Annotation;

import static com.google.common.base.Preconditions.checkNotNull;

abstract class AbstractHttpClientModule
        implements Module
{
    protected final String name;
    protected final Class<? extends Annotation> annotation;
    protected Binder binder;

    protected AbstractHttpClientModule(String name, Class<? extends Annotation> annotation)
    {
        this.name = checkNotNull(name, "name is null");
        this.annotation = checkNotNull(annotation, "annotation is null");
    }

    @Override
    public final void configure(Binder binder)
    {
        this.binder = binder;
        configure();
    }

    public abstract void configure();

    public abstract void addAlias(Class<? extends Annotation> alias);

    public abstract Annotation getFilterQualifier();
}
