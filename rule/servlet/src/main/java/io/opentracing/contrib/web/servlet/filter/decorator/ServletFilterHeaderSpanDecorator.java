package io.opentracing.contrib.web.servlet.filter.decorator;

import io.opentracing.Span;
import io.opentracing.contrib.web.servlet.filter.ServletFilterSpanDecorator;
import io.opentracing.tag.StringTag;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * ServletFilterHeaderSpanDecorator will decorate the span based on incoming HTTP headers.
 * Incoming are compared to the list of {@link #allowedHeaders}, if the header is part of the provided list,
 * they will be added as {@link StringTag}.
 * The tag format will be a concatenation of {@link #prefix} and {@link HeaderEntry#tag}
 */
public class ServletFilterHeaderSpanDecorator implements ServletFilterSpanDecorator {

    public static final String DEFAULT_TAG_PREFIX = "http.header.";
    private final String prefix;
    private final List<HeaderEntry> allowedHeaders;

    /**
     * Constructor of ServletFilterHeaderSpanDecorator with a default prefix of "http.header."
     * @param allowedHeaders list of {@link HeaderEntry} to extract from the incoming request
     */
    public ServletFilterHeaderSpanDecorator(List<HeaderEntry> allowedHeaders) {
        this(allowedHeaders, DEFAULT_TAG_PREFIX);
    }

    /**
     * Constructor of ServletFilterHeaderSpanDecorator
     * @param allowedHeaders list of {@link HeaderEntry} to extract from the incoming request
     * @param prefix the prefix to prepend on each @{@link StringTag}. Can be null is not prefix is desired
     */
    public ServletFilterHeaderSpanDecorator(List<HeaderEntry> allowedHeaders, String prefix) {
        this.allowedHeaders = new ArrayList<>(allowedHeaders);
        this.prefix = (prefix != null && !prefix.isEmpty()) ? prefix : null;
    }

    @Override
    public void onRequest(HttpServletRequest httpServletRequest, Span span) {
        for (HeaderEntry headerEntry : allowedHeaders) {
            String headerValue = httpServletRequest.getHeader(headerEntry.getHeader());
            if (headerValue != null && !headerValue.isEmpty()) {
                buildTag(headerEntry.getTag()).set(span, headerValue);
            }
        }
    }

    @Override
    public void onResponse(HttpServletRequest httpServletRequest,
        HttpServletResponse httpServletResponse, Span span) {
    }

    @Override
    public void onError(HttpServletRequest httpServletRequest,
        HttpServletResponse httpServletResponse, Throwable exception, Span span) {
    }

    @Override
    public void onTimeout(HttpServletRequest httpServletRequest,
        HttpServletResponse httpServletResponse, long timeout, Span span) {
    }

    private StringTag buildTag(String tag) {
        if (prefix == null) {
            return new StringTag(tag);
        }
        return new StringTag(prefix + tag);
    }

    public String getPrefix() {
        return this.prefix;
    }

    public List<HeaderEntry> getAllowedHeaders() {
        return this.allowedHeaders;
    }

    /**
     * HeaderEntry is used to configure {@link ServletFilterHeaderSpanDecorator}
     * {@link #header} is used to check if the header exists using {@link HttpServletRequest#getHeader(String)}
     * {@link #tag} will be used as a {@link StringTag} if {@link #header} is found on the incoming request
     */
    public static class HeaderEntry {
        private final String header;
        private final String tag;

        /**
         * @param header Header on the {@link HttpServletRequest}
         * @param tag Tag to be used if {@link #header} is found
         */
        public HeaderEntry(String header, String tag) {
            this.header = header;
            this.tag = tag;
        }
        public String getHeader() {
            return this.header;
        }

        public String getTag() {
            return this.tag;
        }

    }

}
