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

import com.google.inject.Injector;
import com.proofpoint.bootstrap.Bootstrap;
import com.proofpoint.discovery.client.Announcer;
import com.proofpoint.discovery.client.DiscoveryModule;
import com.proofpoint.event.client.HttpEventModule;
import com.proofpoint.http.server.HttpServerModule;
import com.proofpoint.jmx.JmxModule;
import com.proofpoint.jmx.http.rpc.JmxHttpRpcModule;
import com.proofpoint.json.JsonModule;
import com.proofpoint.log.Logger;
import com.proofpoint.node.NodeModule;
import org.weakref.jmx.guice.MBeanModule;

/**
 * This is the default main-class that should be used in a pom in a ruby/rack project that runs via the platform http rack server.
 */
public class Main
{
    public static void main(String[] args)
            throws Exception
    {
        Bootstrap app = new Bootstrap(
                new NodeModule(),
                new HttpServerModule(),
                new HttpEventModule(),
                new DiscoveryModule(),
                new JsonModule(),
                new MBeanModule(),
                new RackModule(),
                new JmxModule(),
                new JmxHttpRpcModule());

        try {
            Injector injector = app.initialize();
            injector.getInstance(Announcer.class).start();
        }
        catch (Exception e) {
            Logger.get(Main.class).error(e);
            System.err.flush();
            System.out.flush();
            System.exit(0);
        }
        catch (Throwable t) {
            System.exit(0);
        }
    }
}
