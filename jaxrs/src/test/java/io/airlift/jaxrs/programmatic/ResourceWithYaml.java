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
package io.airlift.jaxrs.programmatic;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/")
public class ResourceWithYaml
{
    public record YamlModel(String name) {}

    @POST
    @Path("/yaml")
    @Consumes({"application/yaml", "application/x-yaml", "text/yaml", "text/x-yaml"})
    public String takeYaml(YamlModel model)
    {
        return model.name();
    }

    @POST
    @Path("/badyaml")
    @Consumes("application/yaml")
    public void takeBadYaml(YamlModel ignore)
    {
        // do nothing
    }

    @POST
    @Path("/jsononly")
    @Consumes(APPLICATION_JSON)
    public void takeJsonOnly(YamlModel ignore)
    {
        // do nothing
    }

    @GET
    @Path("/both")
    @Produces({APPLICATION_JSON, "application/yaml"})
    public YamlModel getBoth()
    {
        return new YamlModel("dain");
    }
}
