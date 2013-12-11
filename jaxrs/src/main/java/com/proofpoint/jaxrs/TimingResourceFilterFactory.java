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
package com.proofpoint.jaxrs;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.proofpoint.reporting.ReportCollectionFactory;
import com.sun.jersey.api.model.AbstractMethod;
import com.sun.jersey.spi.container.ContainerRequestFilter;
import com.sun.jersey.spi.container.ContainerResponseFilter;
import com.sun.jersey.spi.container.ResourceFilter;
import com.sun.jersey.spi.container.ResourceFilterFactory;
import org.weakref.jmx.ObjectNameBuilder;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.cache.CacheBuilder.newBuilder;

class TimingResourceFilterFactory
    implements ResourceFilterFactory
{
    private final ReportCollectionFactory reportCollectionFactory;
    private final LoadingCache<String, RequestStats> requestStatsLoadingCache = newBuilder()
            .build(new CacheLoader<String, RequestStats>()
            {
                @Override
                public RequestStats load(String objectName)
                {
                    return reportCollectionFactory.createReportCollection(RequestStats.class, objectName);
                }
            });

    @Inject
    public TimingResourceFilterFactory(ReportCollectionFactory reportCollectionFactory)
    {
        this.reportCollectionFactory = checkNotNull(reportCollectionFactory, "reportCollectionFactory is null");
    }

    @Override
    public List<ResourceFilter> create(AbstractMethod abstractMethod)
    {
        return ImmutableList.<ResourceFilter>of(new TimingResourceFilter(abstractMethod, requestStatsLoadingCache));
    }

    private static class TimingResourceFilter
            implements ResourceFilter
    {
        private final AbstractMethod abstractMethod;
        private final TimingFilter timingFilter;

        private TimingResourceFilter(AbstractMethod abstractMethod, LoadingCache<String, RequestStats> requestStatsLoadingCache)
        {
            this.abstractMethod = abstractMethod;
            String objectName = new ObjectNameBuilder(abstractMethod.getResource().getResourceClass().getPackage().getName())
                    .withProperty("type", abstractMethod.getResource().getResourceClass().getSimpleName())
                    .build();
            RequestStats requestStats = requestStatsLoadingCache.getUnchecked(objectName);
            timingFilter = new TimingFilter(this.abstractMethod, requestStats);
        }

        @Override
        public ContainerRequestFilter getRequestFilter()
        {
            return timingFilter;
        }

        @Override
        public ContainerResponseFilter getResponseFilter()
        {
            return timingFilter;
        }
    }
}
