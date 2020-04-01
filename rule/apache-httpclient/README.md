# SpecialAgent Rule for Apache HttpClient

**Rule Name:** `apache:httpclient`

## Configuration

Following properties are supported by the Apache HttpClient Rule.

### Properties

* `-Dsa.integration.apache:httpclient.spanDecorators`

  Override default provided Apache HttpClient span decorator (`io.opentracing.contrib.specialagent.rule.apache.httpclient.ApacheClientSpanDecorator$StandardTags`), with customized decorator class names, comma separated. The customized decorators must be subclasses of `io.opentracing.contrib.specialagent.rule.apache.httpclient.ApacheClientSpanDecorator` (the interface provided by this rule plugin).

  **Example:**

  ```bash
  com.company.my.project.MySpanDecorator1,com.company.my.project.MySpanDecorator2,io.opentracing.contrib.specialagent.rule.apache.httpclient.ApacheClientSpanDecorator$StandardTags
  ```

* `-Dsa.integration.apache:httpclient.spanDecorators.classpath`

  Indicate the classpath of JARs or directories containing customized decorator classes specified by `sa.integration.apache:httpclient.spanDecorators`, delimited by `File.pathSeparatorChar`.

  **Example:**

  ```
  /path/to/your/lib/myspandecorators1.jar:/path/to/your/lib/myspandecorators1.jar
  ```

## Compatibility

```xml
<groupId>org.apache.httpcomponents</groupId>
<artifactId>httpclient</artifactId>
<version>[4.4,LATEST]</version>
```