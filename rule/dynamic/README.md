# SpecialAgent Rule for Dynamic Instrumentation

**Rule Name:** `dynamic`

## Configuration

Following properties are supported by the Dynamic Instrumentation Rule.

### Properties

* `-Dsa.instrumentation.plugin.dynamic.rules=$RULES`

  Provides dynamic instrumentation rules as per the following specification:

  **Spec**

  ```
  RULE := {[^]CLASS}#{<clinit>|{{<init>|METHOD}[({ARG1,ARG2,...})][:RETURN]}}

  RULES := {RULE}[;RULES]
  ```

  **Key**

  * `{...}`: Required.
  * `[...]`: Optional.
  * `CLASS`: Java identifier of a class name.
  * `<clinit>`: Identifies class initializer.
  * `<init>`: Identifies a constructor.
  * `METHOD`: Java identifier of a method name.
  * `ARG1,ARG2,...`: Java identifiers of argument class names.<br>**Note**: An <ins>empty</ins> argument spec `()` matches methods with no arguments.<br>**Note**: An <ins>absent</ins> argument spec matches methods with any number of arguments.
  * `RETURN`: Java identifier of a class name.<br>**Note**: A `RETURN` spec of `<void>` matches methods returning `void`.<br>**Note**: An <ins>absent</ins> `RETURN` spec matches methods returning any type.
  * `^`: If included in front of a class name, the rule will match the method signature for all subclasses of `CLASS`.
  * `#`: `CLASS`/`METHOD` delimiter.
  * `:`: `METHOD`/`RETURN` delimiter.
  * `;`: Rule delimiter.