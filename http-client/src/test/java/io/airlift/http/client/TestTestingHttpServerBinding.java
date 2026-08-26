package io.airlift.http.client;

import org.junit.jupiter.api.Test;

import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestTestingHttpServerBinding
{
    @Test
    public void testBindsAdvertisedAddressExclusively()
            throws Exception
    {
        try (TestingHttpServer server = new TestingHttpServer(Optional.empty(), new EchoServlet());
                ServerSocket competingSocket = new ServerSocket()) {
            assertThat(server.baseURI().getHost()).isEqualTo("127.0.0.1");
            assertThatThrownBy(() -> competingSocket.bind(new InetSocketAddress(
                    InetAddress.getByName(server.baseURI().getHost()),
                    server.baseURI().getPort())))
                    .isInstanceOf(BindException.class);
        }
    }
}
