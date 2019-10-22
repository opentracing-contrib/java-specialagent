package tomcat;

import io.opentracing.contrib.specialagent.TestUtil;
import io.opentracing.util.GlobalTracer;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Logger;
import javax.servlet.AsyncContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.junit.Test;

public class TomcatAsyncITest {
  private static final Logger logger = Logger.getLogger(TomcatAsyncITest.class.getName());
  private static final int serverPort = 9787;

  public static void main(final String[] args) throws Exception {
    new TomcatAsyncITest().test();
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


    Tomcat.addServlet(appContext, "helloWorldAsyncServlet", new HttpServlet() {
      private static final long serialVersionUID = 6184640156851545023L;

      @Override
      public void doGet(final HttpServletRequest request, final HttpServletResponse response) throws IOException, ServletException {
        if (GlobalTracer.get().activeSpan() == null)
          throw new AssertionError("ERROR: no active span");

        final AsyncContext asyncContext = request.startAsync(request, response);

        new Thread() {

          @Override
          public void run() {
            try {
              if (GlobalTracer.get().activeSpan() == null)
                throw new AssertionError("ERROR: no active span");

              ServletResponse response = asyncContext.getResponse();
              response.setContentType("text/plain");
              PrintWriter out = response.getWriter();
              out.println("Async Servlet active span: " + GlobalTracer.get().activeSpan());
              out.flush();
              asyncContext.complete();
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          }
        }.start();
      }
    }).setAsyncSupported(true);
    appContext.addServletMapping("/async", "helloWorldAsyncServlet");

    tomcatServer.start();
    logger.info("Tomcat server: http://" + tomcatServer.getHost().getName() + ":" + serverPort + "/");

    final URL url = new URL("http://localhost:" + serverPort + "/async");
    final HttpURLConnection connection = (HttpURLConnection)url.openConnection();
    connection.setRequestMethod("GET");
    final int responseCode = connection.getResponseCode();

    if (HttpServletResponse.SC_OK != responseCode)
      throw new AssertionError("ERROR: response: " + responseCode);

    tomcatServer.stop();
    TestUtil.checkSpan("java-web-servlet", 1);
  }
}