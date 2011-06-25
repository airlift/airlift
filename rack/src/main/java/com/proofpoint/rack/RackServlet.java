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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import org.jruby.Ruby;
import org.jruby.RubyInstanceConfig;
import org.jruby.RubyObjectAdapter;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.builtin.IRubyObject;

import javax.inject.Inject;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;

import static org.jruby.javasupport.JavaEmbedUtils.javaToRuby;

public class RackServlet
        implements Servlet
{
    private final IRubyObject rackApplication;
    private final Ruby runtime;
    private final RubyObjectAdapter adapter = JavaEmbedUtils.newObjectAdapter();

    //Servlet config to support the convention of getServletConfig/init
    private ServletConfig servletConfig = null;

    @Inject
    public RackServlet(RackServletConfig config)
            throws IOException
    {
        Preconditions.checkNotNull(config);

        File rackScriptFile = new File(config.getRackConfigPath());

        Preconditions.checkArgument(rackScriptFile.exists(), "Could not find rack script specified by [" + config.getRackConfigPath()
                + "] and resolved to [" + rackScriptFile.getAbsolutePath() + "]");

        runtime = JavaEmbedUtils.initialize(ImmutableList.of(rackScriptFile.getParentFile().getCanonicalPath()), createRuntimeConfig());

        InputStream stream = Resources.getResource("proofpoint/rack.rb").openStream();
        try {
            runtime.loadFile("rack.rb", stream, false);
        }
        finally {
            stream.close();
        }

        IRubyObject builder = runtime.evalScriptlet("Proofpoint::RackServer::Builder.new");

        rackApplication = adapter.callMethod(builder, "build", new IRubyObject[] {
                javaToRuby(runtime, rackScriptFile.getCanonicalPath())
        });
    }

    private RubyInstanceConfig createRuntimeConfig()
    {
        RubyInstanceConfig config = new RubyInstanceConfig();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = ClassLoader.getSystemClassLoader();
        }
        config.setClassCache(JavaEmbedUtils.createClassCache(classLoader));

        URL resource = RubyInstanceConfig.class.getResource("/META-INF/jruby.home");
        if (resource != null && resource.getProtocol().equals("jar")) {
            try { // http://weblogs.java.net/blog/2007/04/25/how-convert-javaneturl-javaiofile
                config.setJRubyHome(resource.toURI().getSchemeSpecificPart());
            }
            catch (URISyntaxException e) {
                config.setJRubyHome(resource.getPath());
            }
        }

        return config;
    }

    @Override
    public void init(ServletConfig servletConfig)
            throws ServletException
    {
        this.servletConfig = servletConfig;
    }

    @Override
    public void service(ServletRequest request, ServletResponse response)
            throws ServletException, IOException
    {
        Preconditions.checkNotNull(request);
        Preconditions.checkNotNull(response);

        Preconditions.checkArgument((request instanceof HttpServletRequest), "Expected a servlet request that implements HttpServletRequest, this servlet only supports Http(s)");
        Preconditions.checkArgument((response instanceof HttpServletResponse), "Expected a servlet response that implements HttpServletResponse, this servlet only supports Http(s)");

        adapter.callMethod(rackApplication, "call",
                new IRubyObject[] {
                        javaToRuby(runtime, request),
                        javaToRuby(runtime, response)
                });
    }

    @Override
    public ServletConfig getServletConfig()
    {
        return servletConfig;
    }

    @Override
    public String getServletInfo()
    {
        return "RackServlet, a servlet for running rack applications.  Copyright 2010 Proofpoint, Inc.";
    }

    @Override
    public void destroy()
    {
    }
}
