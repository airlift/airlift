package io.airlift.jsonrpc;

import io.airlift.jsonrpc.model.JsonRpcResponse;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.WebApplicationException;

import java.util.Optional;

import static io.airlift.jsonrpc.model.JsonRpcErrorCode.CONNECTION_CLOSED;
import static io.airlift.jsonrpc.model.JsonRpcErrorDetail.exception;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;

public class PersonResource
{
    private volatile Optional<Person> person = Optional.empty();

    @JsonRpc("person/get")
    public Person getPerson()
    {
        return person.orElseThrow(() -> new WebApplicationException(NOT_FOUND));
    }

    @JsonRpc("person/put")
    public void setPerson(Person person)
    {
        if (person == null) {
            throw new WebApplicationException("Person cannot be null", NOT_FOUND);
        }
        this.person = Optional.of(person);
    }

    @JsonRpc("person/delete")
    public Person deletePerson()
    {
        Person deletedPerson = person.orElseThrow(() -> new WebApplicationException(NOT_FOUND));
        person = Optional.empty();
        return deletedPerson;
    }

    @Path("person/throws")
    @DELETE
    @JsonRpc
    public void throwsError()
    {
        throw exception(CONNECTION_CLOSED, "Test throws");
    }

    @JsonRpcResult("result")
    public void handleResponse(JsonRpcResponse<ErrorDetail> result)
    {
        if (!result.result().orElseThrow().detail().equals("test")) {
            throw new WebApplicationException("Unexpected error detail", BAD_REQUEST);
        }
    }
}
