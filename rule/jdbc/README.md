# SpecialAgent Rule for Java JDBC API

**Rule Name:** `jdbc`

## Configuration

Following properties are supported by the JDBC Rule.

### Properties

* `-Dsa.integration.jdbc.withActiveSpanOnly`

  Skip tracing on invocations without an active span.

  **Default:** "false"

* `-Dsa.integration.jdbc.ignoreForTracing`

  Skip tracing on specific SQL, identified by the specified SQL clause.

  **Example:** `SELECT 1 FROM dual`.

  Multiple clauses are supported, with default separator of `@@`.

  **Example:** `SELECT 1 FROM dual @@ SELECT 2 FROM dual`

* `-Dsa.integration.jdbc.ignoreForTracing.separator`

  Customized separator is supported for situations where `@@` is not an appropriate separator.