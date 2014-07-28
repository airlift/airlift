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
import com.google.inject.Inject;
import com.proofpoint.reporting.ReportCollectionFactory;
import org.weakref.jmx.ObjectNameBuilder;

import javax.annotation.Nonnull;
import javax.ws.rs.Path;
import javax.ws.rs.container.DynamicFeature;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.ext.Provider;

import java.lang.reflect.Method;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.cache.CacheBuilder.newBuilder;

@Provider
class TimingResourceDynamicFeature
        implements DynamicFeature
{
    private final ReportCollectionFactory reportCollectionFactory;
    private final LoadingCache<String, RequestStats> requestStatsLoadingCache = newBuilder()
            .build(new CacheLoader<String, RequestStats>()
            {
                @Override
                public RequestStats load(@Nonnull String objectName)
                {
                    return reportCollectionFactory.createReportCollection(RequestStats.class, objectName);
                }
            });

    @Inject
    public TimingResourceDynamicFeature(ReportCollectionFactory reportCollectionFactory)
    {
        this.reportCollectionFactory = checkNotNull(reportCollectionFactory, "reportCollectionFactory is null");
    }

    @Override
    public void configure(ResourceInfo resourceInfo, FeatureContext featureContext)
    {
        Class<?> resourceClass = resourceInfo.getResourceClass();
        Method resourceMethod = resourceInfo.getResourceMethod();

        if (resourceClass == null || resourceMethod == null) {
            return;
        }

        if (!isJaxRsResource(resourceClass)) {
            return;
        }

        String objectName = new ObjectNameBuilder(resourceClass.getPackage().getName())
                .withProperty("type", resourceClass.getSimpleName())
                .build();
        RequestStats requestStats = requestStatsLoadingCache.getUnchecked(objectName);

        featureContext.register(new TimingFilter(resourceMethod.getName(), requestStats));
    }

    private static boolean isJaxRsResource(Class<?> type)
    {
        if (type == null) {
            return false;
        }

        else if (type.isAnnotationPresent(Path.class)) {
            return true;
        }
        if (isJaxRsResource(type.getSuperclass())) {
            return true;
        }
        for (Class<?> typeInterface : type.getInterfaces()) {
            if (isJaxRsResource(typeInterface)) {
                return true;
            }
        }

        return false;
    }
}
