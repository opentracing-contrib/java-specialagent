package io.opentracing.contrib.uberjar;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Scanner;

import org.junit.Test;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RegressionIntegrationTest {
  @Test
  public void test() throws Exception {
    final ProcessBuilder builder = new ProcessBuilder("java", "-cp", System.getProperty("java.class.path"), "-javaagent:target/opentracing-uberjar.jar", RegressionIntegrationTest.class.getName());
    builder.inheritIO();
    final Process process = builder.start();
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        process.destroyForcibly();
      }
    });

    final PipedOutputStream out = new PipedOutputStream();
    final PipedInputStream in = new PipedInputStream(out);
    System.setErr(new PrintStream(out));
    try (final Scanner scanner = new Scanner(in)) {
      if (scanner.hasNext())
        assertEquals("Byteman", scanner.nextLine());
    }
  }

  public static void main(final String[] args) throws Exception {
    final String[] classpath = System.getProperty("java.class.path").split(":");
    final URL[] libs = new URL[classpath.length];
    for (int i = 0; i < classpath.length; ++i)
      libs[i] = new File(classpath[i]).toURI().toURL();

    try (final URLClassLoader classLoader = new URLClassLoader(libs, null)) {
      final Class<?> runClass = Class.forName(RegressionIntegrationTest.class.getName(), false, classLoader);
      final Method method = runClass.getMethod("runInURLClassLoader");
      method.invoke(null);
    }

    assertTrue(true);
  }

  private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
  private static final Call.Factory client = new OkHttpClient();

  public static String doGetRequest(final String url) throws IOException {
    final Request request = new Request.Builder().url(url).build();
    final Response response = client.newCall(request).execute();
    return response.body().string();
  }

  public static String doPostRequest(final String url, final String json) throws IOException {
    final RequestBody body = RequestBody.create(JSON, json);
    final Request request = new Request.Builder().url(url).post(body).build();
    final Response response = client.newCall(request).execute();
    return response.body().string();
  }

  public static String makeBowlingJson(final String player1, final String player2) {
    return "{'winCondition':'HIGH_SCORE'," + "'name':'Bowling'," + "'round':4," + "'lastSaved':1367702411696," + "'dateStarted':1367702378785," + "'players':[" + "{'name':'" + player1 + "','history':[10,8,6,7,8],'color':-13388315,'total':39}," + "{'name':'" + player2 + "','history':[6,10,5,10,10],'color':-48060,'total':41}" + "]}";
  }

  public static void runInURLClassLoader() throws InterruptedException, IOException {
    assertEquals(URLClassLoader.class, RegressionIntegrationTest.class.getClassLoader().getClass());
    while (true) {
      // issue the GET request
      final String getResponse = doGetRequest("http://www.vogella.com");
      System.out.println(" GET: \"" + getResponse.substring(0, 10).replace('\n', ' ') + "...\"");

      // issue the POST request
      final String json = makeBowlingJson("Jesse", "Jake");

      final String postResponse = doPostRequest("http://www.roundsapp.com/post", json);
      System.out.println("POST: \"" + postResponse.substring(0, 10).replace('\n', ' ') + "...\"");
      Thread.sleep(1000);
    }
  }
}