/*
 * Copyright 2015 Proofpoint, Inc.
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
package com.proofpoint.bootstrap;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * The AcceptRequests annotation is used on a method that needs to be executed
 * after all @PostConstruct calls are done. This is intended to be applied to
 * methods that start accepting external requests into the service.
 * The method on which the AcceptRequests annotation is applied MUST fulfill
 * all of the following criteria -
 * - The method MUST NOT have any parameters.
 * - The return type of the method MUST be void.
 * - The method MUST NOT throw a checked exception.
 * - The method on which AcceptRequests is applied MAY be public, protected,
 * package private or private.
 * - The method MUST NOT be static except for the application client.
 * - The method MAY be final.
 * @see javax.annotation.PreDestroy
 */
@Documented
@Retention(RUNTIME)
@Target(METHOD)
public @interface AcceptRequests
{
}
