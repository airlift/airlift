/*
 * Copyright 2012 Proofpoint, Inc.
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
package io.airlift.jaxrs;

import com.google.common.base.Throwables;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * Maps ParsingExceptions to a 400 response code.
 */
@Provider
public class ParsingExceptionMapper
        implements ExceptionMapper<ParsingException>
{
    @Override
    public Response toResponse(ParsingException e)
    {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Throwables.getStackTraceAsString(e))
                .build();
    }
}
