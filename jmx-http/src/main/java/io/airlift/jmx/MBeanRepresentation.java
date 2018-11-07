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
package io.airlift.jmx;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import javax.management.Attribute;
import javax.management.Descriptor;
import javax.management.JMException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.io.ByteStreams.nullOutputStream;

@JsonPropertyOrder({"objectName", "className", "description", "descriptor", "attributes", "operations"})
public class MBeanRepresentation
{
    private final ObjectName objectName;
    private final String className;
    private final String description;
    private final Map<String, Object> descriptor;
    private final List<AttributeRepresentation> attributes;
    private final List<OperationRepresentation> operations;

    public MBeanRepresentation(MBeanServer mbeanServer, ObjectName objectName, ObjectMapper objectMapper)
            throws JMException
    {
        this.objectName = objectName;

        MBeanInfo mbeanInfo = mbeanServer.getMBeanInfo(objectName);

        className = mbeanInfo.getClassName();
        description = mbeanInfo.getDescription();
        descriptor = toMap(mbeanInfo.getDescriptor());

        //
        // Attributes
        //
        LinkedHashMap<String, MBeanAttributeInfo> attributeInfos = new LinkedHashMap<>();
        for (MBeanAttributeInfo attributeInfo : mbeanInfo.getAttributes()) {
            attributeInfos.put(attributeInfo.getName(), attributeInfo);
        }

        String[] attributeNames = attributeInfos.keySet().toArray(new String[0]);
        ImmutableList.Builder<AttributeRepresentation> attributes = ImmutableList.builder();
        for (Attribute attribute : mbeanServer.getAttributes(objectName, attributeNames).asList()) {
            String attributeName = attribute.getName();

            // use remove so we only include one value for each attribute
            MBeanAttributeInfo attributeInfo = attributeInfos.remove(attributeName);
            if (attributeInfo == null) {
                // unknown extra attribute, could have been added after MBeanInfo was fetched
                continue;
            }

            Object attributeValue = attribute.getValue();
            AttributeRepresentation attributeRepresentation = new AttributeRepresentation(attributeInfo, attributeValue, objectMapper);
            attributes.add(attributeRepresentation);
        }
        this.attributes = attributes.build();

        //
        // Operations
        //
        ImmutableList.Builder<OperationRepresentation> operations = ImmutableList.builder();
        for (MBeanOperationInfo operationInfo : mbeanInfo.getOperations()) {
            operations.add(new OperationRepresentation(operationInfo));
        }
        this.operations = operations.build();
    }

    @JsonProperty
    public ObjectName getObjectName()
    {
        return objectName;
    }

    @JsonProperty
    public String getClassName()
    {
        return className;
    }

    @JsonProperty
    public String getDescription()
    {
        return description;
    }

    @JsonProperty
    public Map<String, Object> getDescriptor()
    {
        return descriptor;
    }

    @JsonProperty
    public List<AttributeRepresentation> getAttributes()
    {
        return attributes;
    }

    @JsonProperty
    public List<OperationRepresentation> getOperations()
    {
        return operations;
    }

    @JsonPropertyOrder({"name", "type", "description", "readable", "writable", "descriptor", "value"})
    public static class AttributeRepresentation
    {
        private final String name;
        private final String type;
        private final String description;
        private final boolean readable;
        private final boolean writable;
        private final Map<String, Object> descriptor;
        private final Object value;

        private AttributeRepresentation(MBeanAttributeInfo attributeInfo, Object value, ObjectMapper objectMapper)
        {
            if (canSerialize(value, objectMapper)) {
                this.value = value;
                readable = attributeInfo.isReadable();
                writable = attributeInfo.isWritable();
            }
            else {
                this.value = null;
                readable = false;
                writable = false;
            }

            name = attributeInfo.getName();
            type = attributeInfo.getType();
            description = attributeInfo.getDescription();

            descriptor = toMap(attributeInfo.getDescriptor());
        }

        private static boolean canSerialize(Object value, ObjectMapper objectMapper)
        {
            if (value == null) {
                return true;
            }

            // Jackson is not smart enough in the canSerialize check (especially with collections) so
            // the only good way to check if something can be serialized it to serialize it
            // We could save off the serialized data but it looks wrong when pretty printing is enabled
            try {
                objectMapper.writeValue(nullOutputStream(), value);
                return true;
            }
            catch (Exception e) {
                return false;
            }
        }

        @JsonProperty
        public String getName()
        {
            return name;
        }

        @JsonProperty
        public String getType()
        {
            return type;
        }

        @JsonProperty
        public String getDescription()
        {
            return description;
        }

        @JsonProperty
        public boolean isReadable()
        {
            return readable;
        }

        @JsonProperty
        public boolean isWritable()
        {
            return writable;
        }

        @JsonProperty
        public Map<String, Object> getDescriptor()
        {
            return descriptor;
        }

        @JsonProperty
        public Object getValue()
        {
            return value;
        }
    }

    @JsonPropertyOrder({"name", "impact", "returnType", "descriptor", "parameters"})
    public static class OperationRepresentation
    {
        private final String name;
        private final int impact;
        private final String returnType;
        private final List<ParameterRepresentation> parameters;
        private final Map<String, Object> descriptor;

        private OperationRepresentation(MBeanOperationInfo operationInfo)
        {
            name = operationInfo.getName();
            impact = operationInfo.getImpact();
            returnType = operationInfo.getReturnType();

            ImmutableList.Builder<ParameterRepresentation> parameters = ImmutableList.builder();
            for (MBeanParameterInfo parameterInfo : operationInfo.getSignature()) {
                parameters.add(new ParameterRepresentation(parameterInfo));
            }
            this.parameters = parameters.build();
            descriptor = toMap(operationInfo.getDescriptor());
        }

        @JsonProperty
        public String getName()
        {
            return name;
        }

        @JsonProperty
        public int getImpact()
        {
            return impact;
        }

        @JsonProperty
        public String getReturnType()
        {
            return returnType;
        }

        @JsonProperty
        public List<ParameterRepresentation> getParameters()
        {
            return parameters;
        }

        @JsonProperty
        public Map<String, Object> getDescriptor()
        {
            return descriptor;
        }
    }

    @JsonPropertyOrder({"name", "type", "description", "descriptor"})
    public static class ParameterRepresentation
    {
        private final String name;
        private final String description;
        private final String type;
        private final Map<String, Object> descriptor;

        public ParameterRepresentation(MBeanParameterInfo parameterInfo)
        {
            name = parameterInfo.getName();
            description = parameterInfo.getDescription();
            type = parameterInfo.getType();
            descriptor = toMap(parameterInfo.getDescriptor());
        }

        @JsonProperty
        public String getName()
        {
            return name;
        }

        @JsonProperty
        public String getDescription()
        {
            return description;
        }

        @JsonProperty
        public String getType()
        {
            return type;
        }

        @JsonProperty
        public Map<String, Object> getDescriptor()
        {
            return descriptor;
        }
    }

    private static Map<String, Object> toMap(Descriptor descriptor)
    {
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
        for (String fieldName : descriptor.getFieldNames()) {
            Object fieldValue = descriptor.getFieldValue(fieldName);
            if (fieldValue != null) {
                if (fieldValue instanceof Descriptor) {
                    fieldValue = toMap((Descriptor) fieldValue);
                }
                builder.put(fieldName, fieldValue);
            }
        }
        ImmutableMap<String, Object> map = builder.build();
        if (!map.isEmpty()) {
            return map;
        }
        else {
            return null;
        }
    }
}
