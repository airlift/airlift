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
package io.airlift.http.client;

/**
 * Intercepts a synchronous HTTP exchange.
 * <p>
 * An interceptor can rewrite a request, short-circuit an exchange, or wrap the
 * response returned by {@link Chain#proceed(Request)}. Interceptors that wrap a
 * response must preserve its close behavior. Registered interceptor instances
 * can be invoked concurrently by independent client calls.
 */
@FunctionalInterface
public interface HttpClientInterceptor
{
    StreamingResponse intercept(Chain chain);

    interface Chain
    {
        /**
         * Returns the request presented to this interceptor.
         */
        Request request();

        /**
         * Continues the exchange with the supplied request.
         * <p>
         * An interceptor can call this method zero or more times. Every
         * returned response that is not returned to the caller must be closed
         * by the interceptor.
         */
        StreamingResponse proceed(Request request);
    }
}
