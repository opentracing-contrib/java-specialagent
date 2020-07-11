/*
 * Copyright 2018 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.opentracing.contrib.specialagent.rule.grizzly.http.server;

import io.opentracing.propagation.TextMap;
import org.glassfish.grizzly.http.HttpRequestPacket;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * @author Jose Montoya
 */
public class GizzlyHttpRequestPacketAdapter implements TextMap {
    private final HttpRequestPacket requestPacket;
    private final Map<String, String> headers;

    public GizzlyHttpRequestPacketAdapter(HttpRequestPacket requestPacket) {
        this.requestPacket = requestPacket;
        this.headers = new HashMap<>(requestPacket.getHeaders().size());
        for (String headerName : requestPacket.getHeaders().names()) {
            headers.put(headerName, requestPacket.getHeaders().getHeader(headerName));
        }
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        return headers.entrySet().iterator();
    }

    @Override
    public void put(String key, String value) {
        requestPacket.addHeader(key, value);
    }
}
