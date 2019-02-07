package io.opentracing.contrib.specialagent.cassandra;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockTracer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AgentRunner.class)
@AgentRunner.Config(verbose=true, isolateClassLoader=false)
public class CassandraTest {


  @Test
  public void test(final MockTracer tracer) throws ClassNotFoundException {
    Class.forName(Cluster.Initializer.class.getName());

    try {
      Session session = createSession();
      System.out.println("SESSION: " + session);
    } catch (Exception e) {
      e.printStackTrace();
    }
    int size = tracer.finishedSpans().size();
    System.out.println("SPANS: " + size);
  }

  private Session createSession() {
    Cluster cluster = Cluster.builder().addContactPoints("127.0.0.1").withPort(9042).build();
    return cluster.connect();
  }
}
