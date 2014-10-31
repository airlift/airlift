package io.airlift.http.server;

import javax.ws.rs.core.Response;

public interface ResponseGenerator {
  public Response getResponse();
}
