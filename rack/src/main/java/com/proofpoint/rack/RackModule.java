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
package com.proofpoint.rack;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.http.server.TheServlet;

import javax.servlet.Servlet;
import java.util.Collections;
import java.util.Map;

public class RackModule implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.requireExplicitBindings();
        binder.disableCircularProxies();

        binder.bind(Servlet.class).annotatedWith(TheServlet.class).to(RackServlet.class).in(Scopes.SINGLETON);
        ConfigurationModule.bindConfig(binder).to(RackServletConfig.class);
    }

    /**
     * This is a provider that is expected by our http-server module, so it doesn't serve any purpose for us right now, it just allows Guice to create it's bindings.
     * @return an empty map
     */
    @Provides
    @TheServlet
    public Map<String, String> createTheServletParams()
    {
        return Collections.<String,String>emptyMap();
    }
}
