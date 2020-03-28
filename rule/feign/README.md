# SpecialAgent Rule for Feign

**Rule Name:** `feign`

## Configuration

Following properties are supported by the Feign Rule.

### Properties

* `-Dsa.instrumentation.plugin.feign.spanDecorators`

  Override default provided Feign span decorator, with customized decorator class names, comma separated. The customized decorators must be subclasses of `feign.opentracing.FeignSpanDecorator`.

  **Example:**

  ```bash
  com.company.my.project.MySpanDecorator1,com.company.my.project.MySpanDecorator2,feign.opentracing.FeignSpanDecorator$StandardTags
  ```

* `-Dsa.instrumentation.plugin.feign.spanDecorators.classpath`

  Indicate the casspath of JARs or directories containing customized decorator classes specified by `sa.instrumentation.plugin.feign.spanDecorators`, delimited by `File.pathSeparatorChar`.

  **Example:**

  ```
  /path/to/your/lib/myspandecorators1.jar:/path/to/your/lib/myspandecorators1.jar
  ```

## Compatibility

```xml
<groupId>io.github.openfeign</groupId>
<artifactId>feign-core</artifactId>
<version>[9.0.0,LATEST]</version>
```