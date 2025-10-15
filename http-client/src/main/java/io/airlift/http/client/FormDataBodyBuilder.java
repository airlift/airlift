package io.airlift.http.client;

import static com.google.common.net.UrlEscapers.urlFormParameterEscaper;
import static io.airlift.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static java.nio.charset.StandardCharsets.UTF_8;

public class FormDataBodyBuilder {
    private final StringBuilder buffer = new StringBuilder();

    public FormDataBodyBuilder addField(String name, String value) {
        if (buffer.length() > 0) {
            buffer.append("&");
        }
        buffer.append(urlFormParameterEscaper().escape(name))
                .append("=")
                .append(urlFormParameterEscaper().escape(value));
        return this;
    }

    public StaticBodyGenerator build() {
        return createStaticBodyGenerator(buffer.toString(), UTF_8);
    }
}
