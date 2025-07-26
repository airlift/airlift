/*
 * Copyright Starburst Data, Inc. All rights reserved.
 *
 * THIS IS UNPUBLISHED PROPRIETARY SOURCE CODE OF STARBURST DATA.
 * The copyright notice above does not evidence any
 * actual or intended publication of such source code.
 *
 * Redistribution of this material is strictly prohibited.
 */
package io.airlift.jsonrpc.binding;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

import static java.util.Objects.requireNonNull;

record RpcMetadata(String basePath, Map<String, MethodMetadata> methodMap)
{
    record MethodMetadata(Class<?> clazz, String httpMethod, String methodPath, String source)
    {
        public MethodMetadata
        {
            requireNonNull(clazz, "clazz is null");
            requireNonNull(httpMethod, "httpMethod is null");
            requireNonNull(methodPath, "methodPath is null");
            requireNonNull(source, "source is null");
        }
    }

    RpcMetadata
    {
        requireNonNull(basePath, "basePath is null");
        methodMap = ImmutableMap.copyOf(methodMap);
    }
}
