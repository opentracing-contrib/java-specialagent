# SpecialAgent Rule for Java JDBC API

## Configuration

Following properties are supported by JDBC Rule only.

### Properties

* `-Dsa.instrumentation.plugin.jdbc.withActiveSpanOnly`

Skip tracing on invocations without a active span by setting value: `true`.

* `-Dsa.instrumentation.plugin.jdbc.ignoreForTracing`

Skip tracing on specific SQL by setting the value as the SQL clause, such as `select 1 from dual`. Multi-value is supported, default separator is `@@`, for example: `select 1 from dual @@ select 2 from dual`

* `-Dsa.instrumentation.plugin.jdbc.ignoreForTracing.separator`

Customized separator is supported for some cases that `@@` is not a appropriate separator.

