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
package io.airlift.http.client.jetty;

import ch.qos.logback.core.LayoutBase;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;

class HttpClientLogLayout
        extends LayoutBase<HttpRequestEvent>
{
    private static final DateTimeFormatter ISO_FORMATTER = ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());

    @Override
    public String doLayout(HttpRequestEvent event)
    {
        StringBuilder builder = new StringBuilder();
        builder.append(ISO_FORMATTER.format(event.getTimeStamp()))
                .append('\t')
                .append(event.getProtocolVersion())
                .append('\t')
                .append(event.getMethod())
                .append('\t')
                .append(event.getRequestUri()) // TODO: escape
                .append('\t')
                .append(event.getResponseCode())
                .append('\t')
                .append(event.getResponseSize())
                .append('\t')
                .append(event.getTimeToLastByte())
                .append('\t')
                .append(event.getTraceToken())
                .append('\n');
        return builder.toString();
    }
}
