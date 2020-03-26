# SpecialAgent Rule for Apache HttpClient

**Rule Name:** `apache:httpclient`

## Configuration

Following properties are supported by the Apache HttpClient Rule.

### Properties

* `-Dsa.instrumentation.plugin.apache:httpclient.spanDecorators`

  Override default Apache HttpClient span decorator (`io.opentracing.contrib.specialagent.rule.apache.httpclient.ApacheClientSpanDecorator$StandardTags` the class provided by this rule plugin), with customized decorator class names, comma separated. The customized decorators must be subclasses of `io.opentracing.contrib.specialagent.rule.apache.httpclient.ApacheClientSpanDecorator` (the interface provided by this rule plugin).

  **Example:**

  `com.company.my.project.MySpanDecorator1,com.company.my.project.MySpanDecorator2,io.opentracing.contrib.specialagent.rule.apache.httpclient.ApacheClientSpanDecorator$StandardTags`

* `-Dsa.instrumentation.plugin.apache:httpclient.spanDecorators.classpath`

  Indicate the casspath of JARs or directories containing customized decorator classes specified by `sa.instrumentation.plugin.apache:httpclient.spanDecorators`, delimited by `File.pathSeparatorChar`.

  **Example:**

  `/path/to/your/lib/myspandecorators1.jar:/path/to/your/lib/myspandecorators1.jar`

## Compatibility

```xml
<groupId>org.apache.httpcomponents</groupId>
<artifactId>httpclient</artifactId>
<version>[4.4,LATEST]</version>
```