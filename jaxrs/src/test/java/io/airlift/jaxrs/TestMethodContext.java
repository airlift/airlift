/*
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
package io.airlift.jaxrs;

import io.airlift.http.client.HttpClient;
import io.airlift.http.client.Request;
import io.airlift.http.client.StringResponseHandler;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.http.server.testing.TestingHttpServer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static io.airlift.jaxrs.TestOverrideMethodFilterInHttpServer.createServer;
import static io.airlift.testing.Closeables.closeQuietly;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN_TYPE;
import static org.testng.Assert.assertEquals;

@Test(singleThreaded = true)
public class TestMethodContext
{
    private HttpClient client;
    private ExecutorService executor;

    @BeforeClass
    public void setup()
    {
        client = new JettyHttpClient();
        executor = newCachedThreadPool();
    }

    @AfterClass(alwaysRun = true)
    public void teardown()
    {
        closeQuietly(client);
        executor.shutdownNow();
    }

    @Test
    public void testContextThreading()
            throws Exception
    {
        TestingHttpServer server = createServer(new TestingResource());
        try {
            Request request = prepareGet()
                    .setUri(server.getBaseUrl().resolve("/test"))
                    .build();
            StringResponseHandler.StringResponse response = client.execute(request, createStringResponseHandler());
            assertEquals(response.getBody(), "path=test");
        }
        finally {
            server.stop();
        }
    }

    @Path("/test")
    public class TestingResource
    {
        @GET
        public Response getData(@Context UriInfo uriInfo)
                throws ExecutionException, InterruptedException
        {
            String path = executor.submit((Callable<String>) uriInfo::getPath).get();

            return Response.ok("path=" + path, TEXT_PLAIN_TYPE).build();
        }
    }
}
