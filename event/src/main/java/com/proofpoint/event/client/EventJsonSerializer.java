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
package com.proofpoint.event.client;

import org.codehaus.jackson.map.JsonSerializer;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EventJsonSerializer
{

    private static final Pattern HOST_EXCEPTION_MESSAGE_PATTERN = Pattern.compile("([-_a-zA-Z0-9]+):.*");

    private EventJsonSerializer()
    {
    }

    public static <T> JsonSerializer<T> createEventJsonSerializer(EventTypeMetadata<T> eventTypeMetadata, int version)
    {
        switch (version) {
            case 1:
                return EventJsonSerializerV1.createEventJsonSerializer(eventTypeMetadata);

            case 2:
                return EventJsonSerializerV2.createEventJsonSerializer(eventTypeMetadata);

            default:
                throw new RuntimeException(String.format("EventJsonSerializer version %d is unknown", version));
        }
    }

    static String getLocalHostName()
    {
        try {
            return InetAddress.getLocalHost().getHostName();
        }
        catch (UnknownHostException e) {
            // Java 7u5 and later on MacOS sometimes throws this unless the local hostname is in DNS
            // or hosts file. The exception message is the hostname followed by a colon and an error message.
            final Matcher matcher = HOST_EXCEPTION_MESSAGE_PATTERN.matcher(e.getMessage());
            if (matcher.matches()) {
                return matcher.group(1);
            }
            return "unknown";
        }
    }
}
