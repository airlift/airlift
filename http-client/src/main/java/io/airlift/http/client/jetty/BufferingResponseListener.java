package io.airlift.http.client.jetty;

import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.Result;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.io.ByteBufferAccumulator;
import org.eclipse.jetty.io.ByteBufferPool;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public abstract class BufferingResponseListener
        implements Response.Listener
{
    private final ByteBufferAccumulator buffer;
    private final int maxLength;
    private String mediaType;
    private String encoding;

    /**
     * Creates an instance with a default maximum length of 2 MiB.
     */
    public BufferingResponseListener(ByteBufferPool byteBufferPool)
    {
        this(byteBufferPool, 2 * 1024 * 1024);
    }

    /**
     * Creates an instance with the given maximum length
     *
     * @param maxLength the maximum length of the content
     */
    public BufferingResponseListener(ByteBufferPool byteBufferPool, int maxLength)
    {
        this.buffer = new ByteBufferAccumulator(requireNonNull(byteBufferPool, "byteBufferPool is null"), true);
        if (maxLength < 0) {
            throw new IllegalArgumentException("Invalid max length " + maxLength);
        }
        this.maxLength = maxLength;
    }

    @Override
    public void onHeaders(Response response)
    {
        Request request = response.getRequest();
        HttpFields headers = response.getHeaders();
        long length = headers.getLongField(HttpHeader.CONTENT_LENGTH);
        if (HttpMethod.HEAD.is(request.getMethod())) {
            length = 0;
        }
        if (length > maxLength) {
            response.abort(new IllegalArgumentException("Buffering capacity " + maxLength + " exceeded"));
            return;
        }

        String contentType = headers.get(HttpHeader.CONTENT_TYPE);
        if (contentType != null) {
            String media = contentType;

            String charset = "charset=";
            int index = contentType.toLowerCase(Locale.ENGLISH).indexOf(charset);
            if (index > 0) {
                media = contentType.substring(0, index);
                String encoding = contentType.substring(index + charset.length());
                // Sometimes charsets arrive with an ending semicolon.
                int semicolon = encoding.indexOf(';');
                if (semicolon > 0) {
                    encoding = encoding.substring(0, semicolon).trim();
                }
                // Sometimes charsets are quoted.
                int lastIndex = encoding.length() - 1;
                if (encoding.charAt(0) == '"' && encoding.charAt(lastIndex) == '"') {
                    encoding = encoding.substring(1, lastIndex).trim();
                }
                this.encoding = encoding;
            }

            int semicolon = media.indexOf(';');
            if (semicolon > 0) {
                media = media.substring(0, semicolon).trim();
            }
            this.mediaType = media;
        }
    }

    @Override
    public void onContent(Response response, ByteBuffer content)
    {
        int length = content.remaining();
        if (length == 0) {
            return;
        }
        buffer.copyBuffer(content);
    }

    @Override
    public abstract void onComplete(Result result);

    public String getMediaType()
    {
        return mediaType;
    }

    public String getEncoding()
    {
        return encoding;
    }

    /**
     * @return the content as bytes
     * @see #getContentAsString()
     */
    public byte[] getContent()
    {
        if (buffer.getLength() == 0) {
            return new byte[0];
        }
        return buffer.takeByteBuffer().array();
    }

    /**
     * @return the content as a string, using the "Content-Type" header to detect the encoding
     * or defaulting to UTF-8 if the encoding could not be detected.
     * @see #getContentAsString(String)
     */
    public String getContentAsString()
    {
        return getContentAsString(Optional.ofNullable(encoding)
                .orElse(StandardCharsets.UTF_8.name()));
    }

    /**
     * @param encoding the encoding of the content bytes
     * @return the content as a string, with the specified encoding
     * @see #getContentAsString()
     */
    public String getContentAsString(String encoding)
    {
        return getContentAsString(Charset.forName(encoding));
    }

    /**
     * @param encoding the encoding of the content bytes
     * @return the content as a string, with the specified encoding
     * @see #getContentAsString()
     */
    public String getContentAsString(Charset encoding)
    {
        if (buffer.getLength() == 0) {
            return null;
        }
        ByteBuffer output = buffer.takeByteBuffer(); // Releases a buffer
        return new String(output.array(), 0, output.remaining(), encoding);
    }

    /**
     * @return Content as InputStream
     */
    public InputStream getContentAsInputStream()
    {
        if (buffer.getLength() == 0) {
            return new ByteArrayInputStream(new byte[0]);
        }
        ByteBuffer output = buffer.takeByteBuffer(); // Releases a buffer
        return new ByteArrayInputStream(output.array(), output.arrayOffset(), output.remaining());
    }
}
