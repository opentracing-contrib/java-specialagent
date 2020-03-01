package io.opentracing.contrib.specialagent.adaption;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.grack.nanojson.JsonObject;

class AdaptionInput extends Adaption {
  /**
   * Tries to parse the specified string as a regular expression, and returns a
   * {@link Pattern} instance if and only if the pattern <u>can match more than
   * 1 string (i.e. "abc" is technically a regular expression, however it can
   * only match a single string: "abc")</u>.
   *
   * @param string The {@link String} to test.
   * @return A {@link Pattern} instance if and only if the pattern can match
   *         more than 1 string, otherwise {@code null}.
   */
  public static Pattern parseRegex(final String string) {
    if (string == null)
      return null;

    final Pattern pattern;
    try {
      pattern = Pattern.compile(string);
    }
    catch (final PatternSyntaxException e) {
      return null;
    }

    final int len = string.length();
    boolean escaped = false;
    boolean hasOpenBracket = false;
    boolean hasOpenBrace = false;
    boolean hasOpenParentheses = false;
    for (int i = 0; i < len; ++i) {
      final char ch = string.charAt(i);
      if (i == 0 && ch == '^' || i == len - 1 && !escaped && ch == '$')
        return pattern;

      if (escaped) {
        if (ch == 'd' || ch == 'D' || ch == 's' || ch == 'S' || ch == 'w' || ch == 'W' || ch == 'b' || ch == 'B' || ch == 'A' || ch == 'G' || ch == 'Z' || ch == 'z' || ch == 'Q' || ch == 'E')
          return pattern;
      }
      else if (!hasOpenBracket && ch == '[')
        hasOpenBracket = true;
      else if (!hasOpenBrace && ch == '{')
        hasOpenBrace = true;
      else if (!hasOpenParentheses && ch == '(')
        hasOpenParentheses = true;
      else if (ch == '.' || (i > 0 && (ch == '?' || ch == '*' || ch == '+' || (hasOpenBracket && ch == ']') || (hasOpenBrace && ch == '}') || (hasOpenParentheses && ch == ')') || ch == '|'))) {
        return pattern;
      }

      escaped ^= ch == '\\';
    }

    return null;
  }

  private static final String[] escaped = {"\\", ".", "?", "*", "+", "[", "]", "{", "}", "(", ")", "|"};

  static String unescape(String str) {
    if (str.startsWith("^"))
      str = str.substring(1);

    for (int i = 0; i < escaped.length; ++i)
      str = str.replace("\\" + escaped[i], escaped[i]);

    if (str.endsWith("$") && !str.endsWith("\\$"))
      str = str.substring(0, str.length() - 1);

    return str;
  }

  AdaptionInput(final JsonObject object, final String subject) {
    super(object, subject + ".input");
    if (value instanceof String) {
      final Pattern pattern = parseRegex((String)value);
      value = pattern != null ? pattern : unescape((String)value);
    }
  }
}