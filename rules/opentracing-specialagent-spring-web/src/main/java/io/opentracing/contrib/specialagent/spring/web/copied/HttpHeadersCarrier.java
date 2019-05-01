package io.opentracing.contrib.specialagent.spring.web.copied;

import io.opentracing.propagation.TextMap;
import org.springframework.http.HttpHeaders;

import java.util.Iterator;
import java.util.Map;

/**
 * @author Pavol Loffay
 */
public class HttpHeadersCarrier implements TextMap {

  private HttpHeaders httpHeaders;

  public HttpHeadersCarrier(HttpHeaders httpHeaders)  {
    this.httpHeaders = httpHeaders;
  }

  @Override
  public void put(String key, String value) {
    httpHeaders.add(key, value);
  }

  @Override
  public Iterator<Map.Entry<String, String>> iterator() {
    throw new UnsupportedOperationException("Should be used only with tracer#inject()");
  }
}

