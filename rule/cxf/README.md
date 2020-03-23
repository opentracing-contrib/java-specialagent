# SpecialAgent Rule for Apache CXF

**Rule Name:** `cxf`

## Configuration

Following properties are supported by the CXF Rule.

### Properties

* `-Dsa.instrumentation.plugin.cxf.interceptors.server.in`

  Add interceptors to server handling `in` phases. The interceptorss must be subclasses of `org.apache.cxf.phase.PhaseInterceptor<Message>`.

  **Example:**

  `com.company.my.project.MyInterceptor1,com.company.my.project.MyInterceptor2`

* `-Dsa.instrumentation.plugin.cxf.interceptors.server.out`

  Add interceptors to server process `out` phases. The interceptorss must be subclasses of `org.apache.cxf.phase.PhaseInterceptor<Message>`.

* `-Dsa.instrumentation.plugin.cxf.interceptors.client.in`

  Add interceptors to client process `in` phases. The interceptorss must be subclasses of `org.apache.cxf.phase.PhaseInterceptor<Message>`.

* `-Dsa.instrumentation.plugin.cxf.interceptors.client.in`

  Add interceptors to client process `out` phases. The interceptorss must be subclasses of `org.apache.cxf.phase.PhaseInterceptor<Message>`.

* `-Dsa.instrumentation.plugin.cxf.interceptors.classpath`

  Indicate the casspath of JARs or directories containing interceptors classes specified by `sa.instrumentation.plugin.cxf.interceptors.*`, delimited by `File.pathSeparatorChar`.

  **Example:**

  `/path/to/your/lib/myspandecorators1.jar:/path/to/your/lib/myspandecorators1.jar`

## Compatibility

```xml
<groupId>org.apache.cxf</groupId>
<artifactId>cxf-core</artifactId>
<version>[3.3.3,]</version>
```