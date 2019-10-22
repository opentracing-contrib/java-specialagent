package tomcat;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Logger;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.junit.Test;

import io.opentracing.contrib.specialagent.TestUtil;
import io.opentracing.util.GlobalTracer;

public class TomcatSyncITest {
  private static final Logger logger = Logger.getLogger(TomcatSyncITest.class.getName());
  private static final int serverPort = 9786;

  public static void main(final String[] args) throws Exception {
    new TomcatSyncITest().test();
  }

  @Test
  public void test() throws ServletException, LifecycleException, IOException {
    final Tomcat tomcatServer = new Tomcat();
    tomcatServer.setPort(serverPort);

    final File baseDir = new File("target/tomcat");
    tomcatServer.setBaseDir(baseDir.getAbsolutePath());

    final File applicationDir = new File(new File(baseDir, "webapps"), "ROOT");
    applicationDir.mkdirs();

    final Context appContext = tomcatServer.addWebapp("", applicationDir.getAbsolutePath());

    // Following triggers creation of NoPluggabilityServletContext object during
    // initialization
    ((StandardContext)appContext).addApplicationLifecycleListener(new ServletContextListener() {
      @Override
      public void contextInitialized(final ServletContextEvent e) {
      }

      @Override
      public void contextDestroyed(final ServletContextEvent e) {
      }
    });

    Tomcat.addServlet(appContext, "helloWorldServlet", new HttpServlet() {
      private static final long serialVersionUID = 6184640156851545023L;

      @Override
      public void doGet(final HttpServletRequest request, final HttpServletResponse response) throws IOException, ServletException {
        if (GlobalTracer.get().activeSpan() == null)
          throw new AssertionError("ERROR: no active span");

        response.setStatus(HttpServletResponse.SC_ACCEPTED);
      }
    });
    appContext.addServletMapping("/hello", "helloWorldServlet");

    tomcatServer.start();
    logger.info("Tomcat server: http://" + tomcatServer.getHost().getName() + ":" + serverPort + "/");

    final URL url = new URL("http://localhost:" + serverPort + "/hello");
    final HttpURLConnection connection = (HttpURLConnection)url.openConnection();
    connection.setRequestMethod("GET");
    final int responseCode = connection.getResponseCode();

    if (HttpServletResponse.SC_ACCEPTED != responseCode)
      throw new AssertionError("ERROR: response: " + responseCode);

    tomcatServer.stop();
    TestUtil.checkSpan("java-web-servlet", 3);
  }
}