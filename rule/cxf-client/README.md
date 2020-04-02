# SpecialAgent Rule for Apache CXF Client

**Rule Name:** `cxf-client`

## Configuration

Following properties are supported by the cxf-client Rule.

### Properties

* `-Dsa.integration.cxf-client.interceptors.in`

  Add interceptors to server handling `in` phases, comma delimited. The interceptors must be subclasses of `org.apache.cxf.phase.PhaseInterceptor<Message>`.

  **Example:**

  ```bash
  com.company.my.project.MyInterceptor1,com.company.my.project.MyInterceptor2
  ```

* `-Dsa.integration.cxf-client.interceptors.out`

  Add interceptors to client process `out` phases, comma delimited. The interceptors must be subclasses of `org.apache.cxf.phase.PhaseInterceptor<Message>`.

* `-Dsa.integration.cxf-client.interceptors.classpath`

  Indicate the classpath of JARs or directories containing interceptors classes specified by `sa.integration.cxf-client.interceptors.*`, delimited by `File.pathSeparatorChar`.

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