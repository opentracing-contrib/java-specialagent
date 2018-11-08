package io.opentracing.contrib.specialagent;

import static org.junit.Assert.*;

import org.junit.Test;

import io.opentracing.contrib.specialagent.Util;

public class FingerprintUtilTest {
  @Test
  public void testRetain() {
    String[] a, b, r;

    a = new String[] {"a", "b", "c", "d"};
    b = new String[] {"a", "b", "c", "d"};
    r = Util.retain(a, b, 0, 0, 0);
    assertArrayEquals(a, r);

    a = new String[] {"b", "c", "d"};
    b = new String[] {"a", "b", "c", "d"};
    r = Util.retain(a, b, 0, 0, 0);
    assertArrayEquals(a, r);

    a = new String[] {"a", "b", "c", "d"};
    b = new String[] {"b", "c", "d"};
    r = Util.retain(a, b, 0, 0, 0);
    assertArrayEquals(b, r);

    a = new String[] {"a", "b", "c"};
    b = new String[] {"a", "b", "c", "d"};
    r = Util.retain(a, b, 0, 0, 0);
    assertArrayEquals(a, r);

    a = new String[] {"a", "b", "c", "d"};
    b = new String[] {"a", "b", "c"};
    r = Util.retain(a, b, 0, 0, 0);
    assertArrayEquals(b, r);

    a = new String[] {"a", "b", "d"};
    b = new String[] {"a", "b", "c", "d"};
    r = Util.retain(a, b, 0, 0, 0);
    assertArrayEquals(a, r);

    a = new String[] {"a", "b", "c", "d"};
    b = new String[] {"a", "b", "d"};
    r = Util.retain(a, b, 0, 0, 0);
    assertArrayEquals(b, r);

    a = new String[] {"a", "c", "d"};
    b = new String[] {"a", "b", "c", "d"};
    r = Util.retain(a, b, 0, 0, 0);
    assertArrayEquals(a, r);

    a = new String[] {"a", "b", "c", "d"};
    b = new String[] {"a", "c", "d"};
    r = Util.retain(a, b, 0, 0, 0);
    assertArrayEquals(b, r);

    a = new String[] {"a", "d"};
    b = new String[] {"a", "b", "c", "d"};
    r = Util.retain(a, b, 0, 0, 0);
    assertArrayEquals(a, r);

    a = new String[] {"a", "b", "c", "d"};
    b = new String[] {"a", "d"};
    r = Util.retain(a, b, 0, 0, 0);
    assertArrayEquals(b, r);

    a = new String[] {"a", "c", "d"};
    b = new String[] {"a", "b", "d"};
    r = Util.retain(a, b, 0, 0, 0);
    assertArrayEquals(new String[] {"a", "d"}, r);

    a = new String[] {"a", "b", "d"};
    b = new String[] {"a", "c", "d"};
    r = Util.retain(a, b, 0, 0, 0);
    assertArrayEquals(new String[] {"a", "d"}, r);

    a = new String[] {"c", "d"};
    b = new String[] {"a", "b", "d"};
    r = Util.retain(a, b, 0, 0, 0);
    assertArrayEquals(new String[] {"d"}, r);

    a = new String[] {"a", "b"};
    b = new String[] {"a", "c"};
    r = Util.retain(a, b, 0, 0, 0);
    assertArrayEquals(new String[] {"a"}, r);

    a = new String[] {"a", "b"};
    b = new String[] {"c", "d"};
    r = Util.retain(a, b, 0, 0, 0);
    assertNull(r);
  }
}