package io.airlift.api.servertests.parameters;

import io.airlift.api.servertests.ServerTestBase;
import io.airlift.http.client.StatusResponseHandler.StatusResponse;
import jakarta.ws.rs.core.UriBuilder;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static org.assertj.core.api.Assertions.assertThat;

public class TestParameterNames
        extends ServerTestBase
{
    public TestParameterNames()
    {
        super(ParameterService.class);
    }

    @Test
    public void testParametersOfTheSameTypeBindIndependently()
    {
        // both parameters are equal as Jersey model parameters, so each must still resolve to its own name
        assertThat(list("colors=red&shapes=square").getStatusCode()).isEqualTo(200);
        assertThat(ParameterService.RECEIVED_COLORS.get()).containsExactly("red");
        assertThat(ParameterService.RECEIVED_SHAPES.get()).containsExactly("square");

        assertThat(list("shapes=circle").getStatusCode()).isEqualTo(200);
        assertThat(ParameterService.RECEIVED_COLORS.get()).isEmpty();
        assertThat(ParameterService.RECEIVED_SHAPES.get()).containsExactly("circle");
    }

    private StatusResponse list(String query)
    {
        URI uri = UriBuilder.fromUri(baseUri).path("public/api/v1/thing").replaceQuery(query).build();
        return httpClient.execute(prepareGet().setUri(uri).build(), createStatusResponseHandler());
    }
}
