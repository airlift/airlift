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
package io.airlift.http.server;

import io.airlift.tracetoken.TraceTokenManager;

import javax.inject.Inject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

class TraceTokenFilter
        implements Filter
{
    public static final String TRACETOKEN_HEADER = "X-Airlift-TraceToken";
    private final TraceTokenManager traceTokenManager;

    @Inject
    public TraceTokenFilter(TraceTokenManager traceTokenManager)
    {
        this.traceTokenManager = traceTokenManager;
    }

    @Override
    public void init(FilterConfig filterConfig)
    {
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException
    {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String token = request.getHeader(TRACETOKEN_HEADER);
        if (token != null) {
            traceTokenManager.registerRequestToken(token);
        }
        else {
            traceTokenManager.createAndRegisterNewRequestToken();
        }
        chain.doFilter(request, response);
    }

    @Override
    public void destroy()
    {
    }
}
