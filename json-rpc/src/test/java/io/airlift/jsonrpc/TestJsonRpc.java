package io.airlift.jsonrpc;

import com.google.common.reflect.TypeToken;
import io.airlift.TestBase;
import io.airlift.http.client.Request;
import io.airlift.http.client.StatusResponseHandler.StatusResponse;
import io.airlift.jsonrpc.model.JsonRpcErrorDetail;
import io.airlift.jsonrpc.model.JsonRpcResponse;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;
import static io.airlift.json.JsonCodec.jsonCodec;
import static io.airlift.jsonrpc.model.JsonRpcErrorCode.CONNECTION_CLOSED;
import static org.assertj.core.api.Assertions.assertThat;

public class TestJsonRpc
        extends TestBase
{
    public TestJsonRpc()
    {
        super(JsonRpcMethod.addAllInClass(JsonRpcModule.builder(), PersonResource.class), binder -> jaxrsBinder(binder).bind(PersonResource.class));
    }

    @Test
    public void testSetPerson()
    {
        Person person = new Person("John", 24);
        String id = UUID.randomUUID().toString();

        Request request = buildRequest(id, "person/put", new TypeToken<>() {}, person);
        StatusResponse putResponse = httpClient.execute(request, createStatusResponseHandler());
        assertThat(putResponse.getStatusCode()).isEqualTo(204);

        request = buildRequest(id, "person/get");
        JsonRpcResponse<Person> personResponse = httpClient.execute(request, createJsonResponseHandler(jsonCodec(new TypeToken<>() {})));
        assertThat(personResponse.id()).isEqualTo(id);
        assertThat(personResponse.result()).contains(person);
        assertThat(personResponse.error()).isEmpty();

        request = buildRequest(id, "person/delete");
        JsonRpcResponse<Person> deleteResponse = httpClient.execute(request, createJsonResponseHandler(jsonCodec(new TypeToken<>() {})));
        assertThat(deleteResponse.id()).isEqualTo(id);
        assertThat(deleteResponse.result()).contains(person);
        assertThat(deleteResponse.error()).isEmpty();

        request = buildRequest(id, "person/get");
        personResponse = httpClient.execute(request, createJsonResponseHandler(jsonCodec(new TypeToken<>() {})));
        assertThat(personResponse.id()).isEqualTo(id);
        assertThat(personResponse.result()).isEmpty();
        assertThat(personResponse.error()).map(JsonRpcErrorDetail::code).contains(404);
    }

    @Test
    public void testException()
    {
        Request request = buildRequest(2468, "person/throws");
        JsonRpcResponse<Object> response = httpClient.execute(request, createJsonResponseHandler(jsonCodec(new TypeToken<>() {})));
        assertThat(response.id()).isEqualTo(2468);
        assertThat(response.result()).isEmpty();
        assertThat(response.error()).map(JsonRpcErrorDetail::code).contains(CONNECTION_CLOSED.code());
        assertThat(response.error()).map(JsonRpcErrorDetail::message).contains("Test throws");
    }

    @Test
    public void testSendResult()
    {
        Request request = buildResponse(1234, new TypeToken<>() {}, Optional.of(new ErrorDetail("test")));
        StatusResponse response = httpClient.execute(request, createStatusResponseHandler());
        assertThat(response.getStatusCode()).isEqualTo(204);
    }
}
