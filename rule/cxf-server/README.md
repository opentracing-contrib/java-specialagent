# SpecialAgent Rule for Apache CXF Server

**Rule Name:** `cxf-server`

## Configuration

Following properties are supported by the cxf-server Rule.

### Properties

* `-Dsa.integration.cxf-server.interceptors.in`

  Add interceptors to server handling `in` phases, comma delimited. The interceptors must be subclasses of `org.apache.cxf.phase.PhaseInterceptor<Message>`.

  **Example:**

  ```bash
  com.company.my.project.MyInterceptor1,com.company.my.project.MyInterceptor2
  ```

* `-Dsa.integration.cxf-server.interceptors.out`

  Add interceptors to server process `out` phases, comma delimited. The interceptors must be subclasses of `org.apache.cxf.phase.PhaseInterceptor<Message>`.

* `-Dsa.integration.cxf-server.interceptors.classpath`

  Indicate the classpath of JARs or directories containing interceptors classes specified by `sa.integration.cxf.interceptors.*`, delimited by `File.pathSeparatorChar`.

  **Example:**

  ```bash
  /path/to/your/lib/myinterceptors1.jar:/path/to/your/lib/myinterceptors2.jar
  ```

## Compatibility

```xml
<groupId>org.apache.cxf</groupId>
<artifactId>cxf-core</artifactId>
<version>[3.3.3,LATEST]</version>
```