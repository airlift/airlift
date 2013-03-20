package io.airlift.http.client.netty;

import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.Request;
import io.airlift.http.client.StringResponseHandler.StringResponse;
import org.testng.annotations.Test;

import java.net.URI;

import static com.google.common.net.HostAndPort.fromParts;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;

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

        try (NettyIoPool provider = new NettyIoPool();
                NettyAsyncHttpClient client = new NettyAsyncHttpClient(config, provider)) {
            StringResponse json = client.execute(request, createStringResponseHandler());
            System.out.println(json.getBody());
        }
    }
}
