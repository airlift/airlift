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
package com.proofpoint.http.server;

import com.google.common.base.Predicate;

import javax.annotation.Nullable;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static com.google.common.base.Predicates.not;

class AdminFilter implements Filter
{
    private static final String ADMIN_PATH = "/admin";
    private static final String ADMIN_PATH_PREFIX = "/admin/";
    private static final Predicate<String> IS_ADMIN_PATH_PREDICATE = new Predicate<String>()
    {
        @Override
        public boolean apply(@Nullable String input)
        {
            if (input == null) {
                return false;
            }

            return input.equals(ADMIN_PATH) || input.startsWith(ADMIN_PATH_PREFIX);
        }
    };
    private final Predicate<String> forThisPortPredicate;

    public AdminFilter(boolean isAdmin)
    {
        if (isAdmin) {
            forThisPortPredicate = IS_ADMIN_PATH_PREDICATE;
        }
        else {
            forThisPortPredicate = not(IS_ADMIN_PATH_PREDICATE);
        }
    }

    @Override
    public void init(FilterConfig filterConfig)
            throws ServletException
    {
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException
    {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        String path = request.getPathInfo();
        if (forThisPortPredicate.apply(path)) {
            chain.doFilter(servletRequest, servletResponse);
        } else {
            HttpServletResponse response = (HttpServletResponse) servletResponse;
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    @Override
    public void destroy()
    {
    }
}
