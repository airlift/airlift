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

import com.proofpoint.units.Duration;
import com.sun.jersey.api.model.AbstractMethod;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;
import com.sun.jersey.spi.container.ContainerResponse;
import com.sun.jersey.spi.container.ContainerResponseFilter;

import static com.google.common.base.Preconditions.checkNotNull;

class TimingFilter
        implements ContainerRequestFilter, ContainerResponseFilter
{
    private static final String START_TIME_KEY = TimingFilter.class.getName() + ".start-time";
    private final AbstractMethod abstractMethod;
    private final RequestStats requestStats;

    TimingFilter(AbstractMethod abstractMethod, RequestStats requestStats)
    {
        this.abstractMethod = checkNotNull(abstractMethod, "abstractMethod is null");
        this.requestStats = checkNotNull(requestStats, "requestStats is null");
    }

    @Override
    public ContainerRequest filter(ContainerRequest request)
    {
        request.getProperties().put(START_TIME_KEY, System.nanoTime());
        return request;
    }

    @Override
    public ContainerResponse filter(ContainerRequest request, ContainerResponse response)
    {
        Long startTime = (Long) request.getProperties().get(START_TIME_KEY);
        requestStats.requestTime(abstractMethod.getMethod().getName(), response.getStatus())
                .add(Duration.nanosSince(startTime));

        return response;
    }
}
