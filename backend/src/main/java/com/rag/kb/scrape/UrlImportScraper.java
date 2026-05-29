package com.rag.kb.scrape;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** 从 Jsoup 文档中提取图片 URL、视频/嵌入链接文本（用于 URL 入库多选抓取） */
public final class UrlImportScraper {

    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");
    private static final Pattern URL_LIKE =
            Pattern.compile("https?://|www\\.|\\[a-[a-z-]+]|BV[0-9A-Za-z]{6,}", Pattern.CASE_INSENSITIVE);

    private UrlImportScraper() {}

    public static List<String> collectImageUrls(Document doc, String pageUrl, int max) {
        Set<String> seen = new LinkedHashSet<>();
        for (Element img : doc.select("img[src]")) {
            if (seen.size() >= max) {
                break;
            }
            String abs = img.absUrl("src");
            if (!isHttpUrl(abs) || abs.startsWith("data:")) {
                continue;
            }
            seen.add(stripFragment(abs));
        }
        for (Element m : doc.select("meta[property=og:image], meta[name=twitter:image], meta[name=twitter:image:src]")) {
            if (seen.size() >= max) {
                break;
            }
            String c = m.attr("content");
            if (c == null || c.isBlank()) {
                continue;
            }
            String abs = resolveAgainstBase(pageUrl, c.trim());
            if (isHttpUrl(abs) && !abs.startsWith("data:")) {
                seen.add(stripFragment(abs));
            }
        }
        return new ArrayList<>(seen);
    }

    public static String extractCleanText(Document doc) {
        Document clone = doc.clone();
        clone.select("script, style, noscript, svg, canvas, form, button, input, textarea, select, option").remove();
        clone.select("header, footer, nav, aside").remove();
        clone.select("[role=navigation], [role=menu], [role=banner], [role=contentinfo], [aria-hidden=true]").remove();

        List<String> lines = new ArrayList<>();
        Element body = clone.body();
        if (body == null) {
            return "";
        }
        for (Element el : body.select("h1, h2, h3, h4, p, li, article, section, main, div")) {
            String text = normalizeText(el.text());
            if (shouldKeepTextLine(text)) {
                lines.add(text);
            }
        }
        if (lines.isEmpty()) {
            String fallback = normalizeText(body.text());
            return shouldKeepTextLine(fallback) ? fallback : "";
        }
        return String.join("\n", new LinkedHashSet<>(lines));
    }

    /**
     * 生成「视频与嵌入链接」附录（Markdown 风格列表），便于向量检索。
     */
    public static String buildVideoLinksSection(Document doc, String pageUrl) {
        StringBuilder sb = new StringBuilder();
        Set<String> seen = new LinkedHashSet<>();
        appendSrcLines(sb, seen, "video", doc.select("video[src]"), "src");
        appendSrcLines(sb, seen, "video-source", doc.select("video source[src]"), "src");
        appendSrcLines(sb, seen, "iframe", doc.select("iframe[src]"), "src");
        appendSrcLines(sb, seen, "embed", doc.select("embed[src]"), "src");
        for (Element o : doc.select("object[data]")) {
            String data = o.absUrl("data");
            addLine(sb, seen, "object-data", data);
        }
        for (Element a : doc.select("a[href]")) {
            if (seen.size() > 200) {
                break;
            }
            String href = a.absUrl("href");
            if (!isHttpUrl(href)) {
                continue;
            }
            String lower = href.toLowerCase(Locale.ROOT);
            if (lower.contains("youtube.com")
                    || lower.contains("youtu.be")
                    || lower.contains("bilibili.com")
                    || lower.contains("v.qq.com")
                    || lower.contains("youku.com")) {
                addLine(sb, seen, "a-media", href);
            }
        }
        return sb.toString();
    }

    private static boolean shouldKeepTextLine(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        if (text.length() < 8) {
            return false;
        }
        if (URL_LIKE.matcher(text).find() && text.length() < 48) {
            return false;
        }
        int digitCount = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isDigit(text.charAt(i))) {
                digitCount++;
            }
        }
        if (digitCount > text.length() * 0.45) {
            return false;
        }
        return true;
    }

    private static String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return MULTI_SPACE.matcher(text.replace('\u00A0', ' ').trim()).replaceAll(" ");
    }

    private static void appendSrcLines(
            StringBuilder sb, Set<String> seen, String kind, Iterable<Element> elements, String attr) {
        for (Element e : elements) {
            String u = e.absUrl(attr);
            addLine(sb, seen, kind, u);
        }
    }

    private static void addLine(StringBuilder sb, Set<String> seen, String kind, String url) {
        if (!isHttpUrl(url) || url.startsWith("data:")) {
            return;
        }
        String n = stripFragment(url);
        if (!seen.add(n)) {
            return;
        }
        sb.append("- [").append(kind).append("] ").append(n).append('\n');
    }

    private static String resolveAgainstBase(String base, String ref) {
        try {
            return URI.create(base).resolve(ref).normalize().toString();
        } catch (Exception e) {
            return ref;
        }
    }

    private static boolean isHttpUrl(String u) {
        if (u == null || u.isBlank()) {
            return false;
        }
        String x = u.strip().toLowerCase(Locale.ROOT);
        return x.startsWith("http://") || x.startsWith("https://");
    }

    private static String stripFragment(String u) {
        int i = u.indexOf('#');
        return i < 0 ? u : u.substring(0, i);
    }
}
