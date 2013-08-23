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

import com.google.inject.Inject;
import com.google.inject.Provider;

class ReportCollectionProvider<T> implements Provider<T>
{
    private final Class<T> iface;
    private ReportCollectionFactory reportCollectionFactory;

    public ReportCollectionProvider(Class<T> iface)
    {
        this.iface = iface;
    }

    @Inject
    public void setReportCollectionFactory(ReportCollectionFactory reportCollectionFactory)
    {
        this.reportCollectionFactory = reportCollectionFactory;
    }

    @Override
    public T get()
    {
        return reportCollectionFactory.createReportCollection(iface);
    }
}
