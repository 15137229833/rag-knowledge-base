package com.rag.kb.scrape;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/** 抓取 URL 二进制内容（网页配图等），带大小上限 */
public final class HttpBinaryFetch {

    private static final HttpClient CLIENT =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();

    private HttpBinaryFetch() {}

    public record Result(byte[] body, String contentType) {}

    public static Result get(String url, long maxBytes, String userAgent) throws Exception {
        HttpRequest req =
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(25))
                        .header("User-Agent", userAgent)
                        .GET()
                        .build();
        HttpResponse<InputStream> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());
        int code = resp.statusCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("HTTP " + code);
        }
        String ct = resp.headers().firstValue("Content-Type").orElse("application/octet-stream");
        String primary = primaryMime(ct);
        try (InputStream in = resp.body()) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] tmp = new byte[8192];
            long total = 0;
            int n;
            while ((n = in.read(tmp)) >= 0) {
                total += n;
                if (total > maxBytes) {
                    throw new IllegalStateException("响应体积超过上限");
                }
                buf.write(tmp, 0, n);
            }
            return new Result(buf.toByteArray(), primary);
        }
    }

    private static String primaryMime(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        String t = contentType.strip().toLowerCase(Locale.ROOT);
        int sc = t.indexOf(';');
        return sc < 0 ? t : t.substring(0, sc).strip();
    }
}
