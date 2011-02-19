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
package com.proofpoint.http.server;

import com.google.inject.Inject;
import org.eclipse.jetty.server.Server;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * Wraps the Jetty Server with Life Cycle annotations
 */
public class JettyServer
{
    private final Server server;

    @Inject
    public JettyServer(Server server)
    {
        this.server = server;
    }

    @PostConstruct
    public void start()
            throws Exception
    {
        server.start();
    }

    @PreDestroy
    public void stop()
            throws Exception
    {
        server.stop();
    }
}
