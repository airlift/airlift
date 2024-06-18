/*
 * Copyright 2010 Proofpoint, Inc.
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
package io.airlift.jaxrs.programmatic;

import com.google.inject.Injector;
import com.google.inject.Module;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.jaxrs.JaxrsModule;
import io.airlift.json.JsonModule;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.model.Resource;
import org.glassfish.jersey.server.model.ResourceMethod;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;
import static org.assertj.core.api.Assertions.assertThat;

public class TestProgrammaticResource
{
    public String getResult()
    {
        return "dummy";
    }

    @Test
    public void testProgrammaticResourceBinding()
            throws NoSuchMethodException
    {
        Resource.Builder builder = Resource.builder();
        ResourceMethod.Builder method = builder.path("/foo/bar").addMethod("GET");
        Method getResultMethod = getClass().getMethod("getResult");
        method.handledBy(this, getResultMethod);
        Resource resource = builder.build();

        Module module = binder -> jaxrsBinder(binder).bindInstance(resource);
        Injector injector = new Bootstrap(module, new JaxrsModule(), new JsonModule()).quiet().initialize();
        ResourceConfig resourceConfig = injector.getInstance(ResourceConfig.class);

        List<ResourceMethod> foundMethods = resourceConfig.getResources().stream()
                .filter(r -> r.getPath().equals("/foo/bar"))
                .flatMap(r -> r.getAllMethods().stream())
                .collect(toImmutableList());
        assertThat(foundMethods.size()).isEqualTo(1);
        ResourceMethod foundMethod = foundMethods.get(0);
        assertThat(foundMethod.getInvocable().getHandlingMethod()).isEqualTo(getResultMethod);
        assertThat(foundMethod.getInvocable().getHandler().getHandlerClass()).isEqualTo(getClass());
    }
}
