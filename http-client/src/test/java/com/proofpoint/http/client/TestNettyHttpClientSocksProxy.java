package com.proofpoint.http.client;

import com.proofpoint.http.client.StringResponseHandler.StringResponse;
import com.proofpoint.http.client.netty.NettyAsyncHttpClient;
import org.testng.annotations.Test;

import java.net.URI;

import static com.google.common.net.HostAndPort.fromParts;
import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.http.client.StringResponseHandler.createStringResponseHandler;

public class TestNettyHttpClientSocksProxy {

    @Test(enabled = false, description = "This test requires a working socks proxy to run")
    public void testGetMethod()
            throws Exception
    {
        // To run this test:
        //
        // 1) create a ssh socks proxy with:
        //
        //      ssh -N -D 1080 any.server.running.sshd
        //
        // 2) replace the uri below with a location only accessible from that host
        //    Alternatively, run with the proxy up and once with the proxy down
        //
        URI uri = URI.create("http://example.com");

        Request request = prepareGet()
                .setUri(uri)
                .build();

        HttpClientConfig config = new HttpClientConfig();
        config.setSocksProxy(fromParts("localhost", 1080));

        try (NettyAsyncHttpClient client = new NettyAsyncHttpClient(config)) {
            StringResponse json = client.execute(request, createStringResponseHandler());
            System.out.println(json.getBody());
        }
    }
}
