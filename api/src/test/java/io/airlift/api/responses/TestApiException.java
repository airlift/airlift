package io.airlift.api.responses;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static org.assertj.core.api.Assertions.assertThat;

public class TestApiException
{
    @Test
    public void testBadRequestHasJsonMediaType()
    {
        assertThat(ApiException.badRequest("Invalid request", List.of("something")))
                .isInstanceOfSatisfying(WebApplicationException.class, exception -> {
                    Response response = exception.getResponse();
                    assertThat(response.getStatus()).isEqualTo(400);
                    assertThat(response.getMediaType()).isEqualTo(APPLICATION_JSON_TYPE);
                    assertThat(response.getEntity()).isEqualTo(new ApiBadRequest(Optional.of("Invalid request"), Optional.of(List.of("something"))));
                });
    }

    @Test
    public void testCustomResponseHasJsonMediaType()
    {
        ApiAlreadyExists error = new ApiAlreadyExists(Optional.of("Already exists"));
        assertThat(ApiException.response(error))
                .isInstanceOfSatisfying(WebApplicationException.class, exception -> {
                    Response response = exception.getResponse();
                    assertThat(response.getStatus()).isEqualTo(409);
                    assertThat(response.getMediaType()).isEqualTo(APPLICATION_JSON_TYPE);
                    assertThat(response.getEntity()).isSameAs(error);
                });
    }
}
