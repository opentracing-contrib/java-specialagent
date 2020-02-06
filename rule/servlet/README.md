# SpecialAgent Rule for Java Servlet API

## Configuration

Following properties are supported by the Servlet Rule.

### Properties

* `-Dsa.instrumentation.plugin.servlet.skipPattern`

  Skip tracing on the url matching specified pattern.

  **Example:** `/health`

* `-Dsa.instrumentation.plugin.servlet.spanDecorators`

  Override default servlet span decorator, with specified customized decorator class name, multiple classes separated by ",". A customized decorator should be a class implements `io.opentracing.contrib.web.servlet.filter.ServletFilterSpanDecorator`

  **Example:** `com.company.my.project.MySpanDecorator1,com.company.my.project.MySpanDecorator2`.
