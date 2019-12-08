package io.opentracing.contrib.specialagent.rule.method;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

/**
 * MethodAgentIntercept
 *
 * @author code98@163.com
 * @date 2019/11/15 5:28 下午
 */
public class MethodAgentIntercept {

    private static final ThreadLocal<Queue<Span>> spanHolder = ThreadLocal.withInitial(LinkedList::new);

    public static final String TAGS_KEY_SPAN_TYPE = "span.type";
    public static final String TAGS_KEY_ORIGIN = "origin";
    public static final String TAGS_KEY_ERROR_MESSAGE = "error.message";
    public static final String TAGS_KEY_ERROR = "error";
    public static final String TAGS_KEY_HTTP_STATUS_CODE = "http.status_code";

    public static final String TAGS_VALUE_INTERNAL = "internal";
    public static final int TAGS_VALUE_500_INT = 500;
    public static final int TAGS_VALUE_200_INT = 200;

    public static void enter(String origin) {

        // parse public java.lang.String com.test.HomeController.homePage(java.lang.String)

        String[] format1 = origin.split(" ");
        String methodNameInfo = format1[2];
        String[] format2 = methodNameInfo.split("\\(");
        String format3 = format2[0];
        String[] format4 = format3.split("\\.");
        String methodName = format4[format4.length - 1];

        final Span span = GlobalTracer.get()
                .buildSpan(methodName)
                .withTag(TAGS_KEY_SPAN_TYPE, TAGS_VALUE_INTERNAL)
                .withTag(TAGS_KEY_ORIGIN, origin)
                .withTag(Tags.COMPONENT.getKey(), "method").start();

        spanHolder.get().add(span);
    }

    public static void exit(Throwable t) {
        final Queue<Span> spans = spanHolder.get();
        if (spans.isEmpty()) {
            return;
        }
        final Span span = spans.poll();
        if (t != null) {
            span.log(errorLogs(t));
            span.setTag(TAGS_KEY_ERROR, true);
            span.setTag(TAGS_KEY_ERROR_MESSAGE, t.getMessage());
            span.setTag(TAGS_KEY_HTTP_STATUS_CODE, TAGS_VALUE_500_INT);
        } else {
            span.setTag(TAGS_KEY_HTTP_STATUS_CODE, TAGS_VALUE_200_INT);
        }
        span.finish();
    }

    private static Map<String, Object> errorLogs(final Throwable throwable) {
        final Map<String, Object> errorLogs = new HashMap<>(2);
        errorLogs.put("event", Tags.ERROR.getKey());
        errorLogs.put("error.object", errorTrackSpace(throwable));
        return errorLogs;
    }

    /**
     * output the throwable info
     *
     * @param e
     * @return
     */
    public static String errorTrackSpace(Throwable e) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        e.printStackTrace(new PrintStream(baos));
        return baos.toString();
    }

}
