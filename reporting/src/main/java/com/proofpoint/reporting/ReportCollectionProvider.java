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

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Throwables.propagate;

class ReportCollectionProvider<T> implements Provider<T>
{
    private final Class<T> iface;
    private final String name;
    private ReportCollectionFactory reportCollectionFactory;

    public ReportCollectionProvider(Class<T> iface)
    {
        this.iface = iface;
        name = null;
    }

    public ReportCollectionProvider(Class<T> iface, String name)
    {
        this.iface = checkNotNull(iface, "iface is null");
        this.name = checkNotNull(name, "name is null");
        try {
            ObjectName.getInstance(name);
        }
        catch (MalformedObjectNameException e) {
            throw propagate(e);
        }
    }

    @Inject
    public void setReportCollectionFactory(ReportCollectionFactory reportCollectionFactory)
    {
        this.reportCollectionFactory = reportCollectionFactory;
    }

    @Override
    public T get()
    {
        return reportCollectionFactory.createReportCollection(iface, name);
    }
}
