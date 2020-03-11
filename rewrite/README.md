The <ins>Rewritable Tracer</ins> allows one to rewrite data in the spans created by <ins>Instrumentation Plugins</ins> without having to modify the source code.

# JSON Configuration

The configuration JSON is comprised of a top-level object that contains properties defining rewrite rules.

### Property Name

The property name for the rule is interpreted as per the guidelines of [Rule Name Pattern](https://github.com/opentracing-contrib/java-specialagent/#rule-name-pattern). This allows one to define rules that match a single or multiple <ins>Instrumentation Plugins</ins>.

## Rule Definitions

The value of the properties in the top-level object is an array of rule definitions.

### Inputs and Outputs

Rules contain `input` (required) and `output` (optional) properties.

#### Input (required)

The `input` property is required, as it defines the rule by which span data is matched. The `input` object contains 3 properties:

* `type`: The type of data to match (`tag`, `log`, or `operationName`).
* `key`: The `key` of the data (a **string**).
* `value`: The `value` of the data (a **boolean**, **number**, or **string** -- if a **string**, then the `value` is interpreted as a regular expression).

**Note:** Depending on the `type`, the `key` or `value` may or may not be relevant. See the use-case examples below for further direction.
**Note:** The `input` property can contain a value with a single `input` object, or an array with multiple `input` objects. If multiple `input` objects are provided, they will be considered with **OR** behavior.

#### Output (optional)

If `output` is omitted, then the matched data will be dropped.

If `output` is provided, then the matched data will be rewritten.

Like the `input` object, the `output` object follows the same schema:

* `type`: The type of data to which the matched data is to be rewritten (`tag`, `log`, or `operationName`).
* `key`: The `key` of the data to which the matched data is to be rewritten (a **string**).<br>If `key` is omitted, then the key of the matched data will not be changed.
* `value`: The `value` of the data (a **boolean**, **number**, or **string** -- if a **string**, then the `value` is interpreted as a regular expression).<br>If `value` is omitted, then the value of the matched data will not be changed.<br>The `value` string may contain back references (i.e. `$1`, `$2`, etc) if the `value` in the `input` defines a regular expression with matching groups.

**Note:** The `output` property can contain a value with a single `output` object, or an array with multiple `output` objects. If multiple `output` objects are provided, they will be considered with **AND** behavior.

# Use-Cases

All features are explained using the following use cases. These features can also be combined - even if this is not mentioned explicitly.

You will get a warning if the configuration has a logical error and the spans will not be modified.
If you have configurations for multiple agent rules and only one of them has a configuration error,
only the faulty configuration will be removed and the other continues to work as expected.

## Blacklisting

Blacklisting is the most common use case to either reduce the traffic/noise - or to redact user information.

### Blacklist a tag

The following rule definition blacklists the `http.url` tag:

```json
{
  "jedis": [
    {
      "input": {
        "type": "tag",
        "key": "http.url"
      }
    }
  ]
}
```

If you only want to blacklist `http.url` if it equals `http://example.com`, add a `value`:

```json
{
  "jedis": [
    {
      "input": {
        "type": "tag",
        "key": "http.url",
        "value": "http://example.com"
      }
    }
  ]
}
```

**`value` is interpreted as a regex.**

If you want to blacklist all `http.url`s that start with `http://`, specify a regex in `value`:

```json
{
  "jedis": [
    {
      "input": {
        "type": "tag",
        "key": "http.url",
        "value": "http://.*"
      }
    }
  ]
}
```

Note
1. The trailing `.*` is necessary, because the regular expression must match the entire tag value.
2. Use the [Java](https://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html) pattern syntax.
3. It's possible to use a number of boolean as `value`, which will not be interpreted as a regular expression.

If you want to blacklist a specific tag whose value is the string literal `Are you happy?`, make sure to escape the `?`, because `value` is interpreted as a regex:

```json
{
  "jedis": [
    {
      "input": {
        "type": "tag",
        "key": "hello",
        "value": "Are you happy\?"
      }
    }
  ]
}
```

### Blacklist a log entry

Blacklisting a log entry uses the same syntax as blacklisting a tag, except that the `type` is `log`.

To blacklist a log event, use

```json
{
  "jedis": [
    {
      "input": {
        "type": "log"
      }
    }
  ]
}
```

This would blacklist all log events (for jedis).
You can restrict the log events to be blacklisted using `value` (see above).

To blacklist a single field from a log entry with fields, use

```json
{
  "jedis": [
    {
      "input": {
        "type": "log",
        "key": "http.method"
      }
    },
    {
      "input": {
        "type": "log",
        "key": "http.url"
      }
    }
  ]
}
```

Only the `http.method` and `http.url` fields will be removed from the log entry. If all fields from the log entry are blacklisted, the entire log entry is blacklisted as well.

### Blacklist an operation name

Every span must have an operation name, hence it's not possible to create a span without an operation name.

However, it's also possible to change the operation name of a span after it has started -
and this call can be blacklisted. The result is the same as if
[setOperationName](https://javadoc.io/doc/io.opentracing/opentracing-api/0.20.2/io/opentracing/Span.html#setOperationName-java.lang.String-) had not been called.

```json
{
  "jedis": [
    {
      "input": {
        "type": "operationName"
      }
    }
  ]
}
```

This would blacklist all calls to `setOperationName` (in jedis).
You can restrict calls to be blacklisted using `value` (see above).

## Advanced use cases

The remaining use case cover advanced scenarios that go beyond blacklisting.

### Transforming values

If you want to strike a better balance between redacting user information and keeping observability,
you can redact specific parts of a value (tag, log or operationName).

```json
{
  "jedis": [
    {
      "input": {
        "type": "tag",
        "key": "db.statement",
        "value": "(select * from user where first_name =).*"
      },
      "output": {
        "type": "tag",
        "value": "$1?"
      }
    }
  ]
}
```

In this example, `$1?` is the [replacement](https://docs.oracle.com/javase/7/docs/api/java/util/regex/Matcher.html#replaceAll(java.lang.String) string. If `value` does not specify a regex, the output `value` would be interpreted as a plain string.

### Multiple Outputs

If you have a tag with a high cardinality (e.g. database statements without wildcards),
you might want to transform the value - but keep the original value somewhere else
(e.g. in a log statement which is not indexed).

The `output` property accepts either a single object, or an array of objects, for instance:

```json
{
  "jedis": [
    {
      "input": {
        "type": "tag",
        "key": "db.statement",
        "value": "(select * from articles) where id =.*"
      },
      "output": [
        {
          "type": "tag",
          "value": "$1"
        },
        {
          "type": "log"
        }
      ]
    }
  ]
}
```

This would shorten the `db.statement` tag to `select * from articles`,
but keep the original statement in a log entry with the same field key (`db.statement`).

The next example uses the same mechanism - using multiple output to have a shorter version somewhere else.
In this case, it is a different tag - and you can specify the output tag using `key`.

```json
{
  "jedis": [
    {
      "input": {
        "type": "tag",
        "key": "http.url",
        "value": "(https?).*"
      },
      "output": [
        {
          "type": "tag",
          "key": "http.protocol",
          "value": "$1"
        },
        {
          "type": "tag"
        }
      ]
    }
  ]
}
```

### Multiple Inputs

Similar to the `output` property, the `input` property accepts either a single object, or an array of objects. This allows one to define rules that differ in the input constraints, but share the same output(s). For instance:

```json
{
  "jedis": [
    {
      "input": [
        {
          "type": "tag",
          "key": "k0",
          "value": "v1"
        },
        {
          "type": "tag",
          "key": "k1",
          "value": "v1"
        }
      ],
      "output": {
        "type": "tag",
        "value": "new"
      }
    }
  ]
}
```

### Multiple Inputs and Outputs

Multiple `input`s and `output`s can be used together, for example:

```json
{
  "jedis": [
    {
      "input": [
        {
          "type": "tag",
          "key": "k0",
          "value": "v1"
        },
        {
          "type": "tag",
          "key": "k1",
          "value": "v1"
        }
      ],
      "output": [
        {
          "type": "tag",
          "value": "new"
        },
        {
          "type": "log"
        }
      ]
    }
  ]
}
```

### Global rules

If you need to apply a rule globally, you can use `all`, e.g. for blacklisting all `http.url` tags:

```json
{
  "*": [
    {
      "input": {
        "type": "tag",
        "key": "http.url"
      }
    }
  ]
}
```

### Arbitrary tags

You can add arbitrary tags when a span is started as follows:

```json
{
  "*": [
    {
      "input": {
        "type": "operationName"
      },
      "output": [
        {
          "type": "tag",
          "key": "service",
          "value": "my_service"
        }
      ]
    }
  ]
}
```

This would add a tag `service` with value `my_service` to all spans in all rules.