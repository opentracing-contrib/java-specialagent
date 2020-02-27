The customizable tracer allows you to change the spans created by agent rules without having to modify the source code.

All features are explained using the following use cases. These features can also be combined - even if this is not mentioned explicitly.

You will get a warning if the configuration has a logical error and the spans will not be modified.
If you have configurations for multiple agent rules and only one of them has a configuration error,
only the faulty configuration will be removed and the other continues to work as expected.

# Blacklisting

Blacklisting is the most common use case to either reduce the traffic/noise - or to redact user information.

## Blacklist a tag

The following rule definition blacklists the `http.url` tag:

```json
{
  "jedis": [
    {
      "type": "tag",
      "key": "http.url"
    }
  ]
}
```

If you only want to blacklist `http.url` if it equals `http://example.com`, add a `value`:

```json
{
  "jedis": [
    {
      "type": "tag",
      "key": "http.url",
      "value": "http://example.com"
    }
  ]
}
```

If you want to blacklist all `http.url`s that start with `http://`, use a `valueRegex`:

```json
{
  "jedis": [
    {
      "type": "tag",
      "key": "http.url",
      "valueRegex": "http://.*"
    }
  ]
}
```

Note
1. The trailing `.*` is necessary, because the regular expression must match the entire tag value
2. Use the [Java](https://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html) pattern syntax

## Blacklist a log entry

Blacklisting a log entry uses the same syntax as blacklisting a tag, except that the `type` is `log`.

To blacklist a log event, use

```json
{
  "jedis": [
    {
      "type": "log"
    }
  ]
}
```

This would blacklist all log events (for jedis).
You can restrict the log events to be blacklisted using `value` or `valueRegex` (see above).

To blacklist a single field from a log entry with fields, use

```json
{
  "jedis": [
    {
      "type": "log",
      "key": "http.method"
    },
    {
      "type": "log",
      "key": "http.url"
    }
  ]
}
```

Only the `http.method` and `http.url` fields will be removed from the log entry. If all fields from the log entry are blacklisted, the entire log entry is blacklisted as well.

## Blacklist an operation name

Every span must have an operation name, hence it's not possible to create a span without an operation name.

However, it's also possible to change the operation name of a span after it has started -
and this call can be blacklisted. The result is the same as if
[setOperationName](https://javadoc.io/doc/io.opentracing/opentracing-api/0.20.2/io/opentracing/Span.html#setOperationName-java.lang.String-) had not been called.

```json
{
  "jedis": [
    {
      "type": "operationName"
    }
  ]
}
```

This would blacklist all calls to `setOperationName` (in jedis).
You can restrict calls to be blacklisted using `value` or `valueRegex` (see above).

# Advanced use cases

The remaining use case cover advanced scenarios that go beyond blacklisting.

## Transforming values

If you want to strike a better balance between redacting user information and keeping observability, final
you can redact specific parts of a value (tag, log or operationName).

```json
{
  "jedis": [
    {
      "type": "tag",
      "key": "db.statement",
      "valueRegex": "(select * from user where first_name =).*",
      "output": [
        {
          "type": "tag",
          "value": "$1?"
        }
      ]
    }
  ]
}
```

In this example, `$1?` is the [replacement](https://docs.oracle.com/javase/7/docs/api/java/util/regex/Matcher.html#replaceAll(java.lang.String) string. If you don't use `valueRegex`, the output `value` would be interpreted as a plain string.

## Multiple Outputs

If you have a tag with a high cardinality (e.g. database statements without wildcards), final
you might want to transform the value - but keep the original value somewhere else
(e.g. in a log statement which is not indexed).

```json
{
  "jedis": [
    {
      "type": "tag",
      "key": "db.statement",
      "valueRegex": "(select * from articles) where id =.*",
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

This would shorten the `db.statement` tag to `select * from articles`, final
but keep the original statement in a log entry with the same field key (`db.statement`).

The next example uses the same mechanism - using multiple output to have a shorter version somewhere else.
In this case, it is a different tag - and you can specify the output tag using `key`.

```json
{
  "jedis": [
    {
      "type": "tag",
      "key": "http.url",
      "valueRegex": "(https?).*",
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

## Global rules

If you need to apply a rule globally, you can use `all`, e.g. for blacklisting all `http.url` tags:

```json
{
  "*": [
    {
      "type": "tag",
      "key": "http.url"
    }
  ]
}
```

## ServiceName

You can create a tag from the service name as follows:

 ```json
{
  "jedis": [
    {
      "type": "serviceName",
      "output": [
        {
          "type": "tag",
          "key": "service"
        }
      ]
    }
  ]
}
```

Note that this feature is only supported for LightStep and Jaeger.
