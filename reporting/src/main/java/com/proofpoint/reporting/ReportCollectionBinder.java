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
import com.google.inject.Scopes;

public class ReportCollectionBinder<T>
{
    private final Binder binder;
    private final Class<T> iface;

    ReportCollectionBinder(Binder binder, Class<T> iface)
    {
        this.binder = binder;
        this.iface = iface;
    }

    public void withGeneratedName()
    {
        binder.bind(iface).toProvider(new ReportCollectionProvider<>(iface)).in(Scopes.SINGLETON);
    }

    public void as(String name)
    {
        binder.bind(iface).toProvider(new ReportCollectionProvider<>(iface, name)).in(Scopes.SINGLETON);
    }
}
