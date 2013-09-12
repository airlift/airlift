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
import com.google.inject.Module;
import com.google.inject.Scopes;

import static com.google.inject.multibindings.Multibinder.newSetBinder;

public class ReportingModule
    implements Module
{
    @Override
    public void configure(Binder binder)
    {
        newSetBinder(binder, Mapping.class);
        binder.bind(ReportExporter.class).asEagerSingleton();
        binder.bind(GuiceReportExporter.class).asEagerSingleton();
        binder.bind(ReportedBeanRegistry.class).in(Scopes.SINGLETON);
        binder.bind(MinuteBucketIdProvider.class).in(Scopes.SINGLETON);
        binder.bind(BucketIdProvider.class).to(MinuteBucketIdProvider.class).in(Scopes.SINGLETON);
        binder.bind(ReportCollectionFactory.class).in(Scopes.SINGLETON);
    }
}
