package com.rag.kb.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class TextChunker {

    public static final int DEFAULT_CHUNK_SIZE = 800;
    public static final int DEFAULT_OVERLAP = 120;

    private static final Pattern PARA_SPLIT = Pattern.compile("\n{2,}");
    private static final Pattern SENTENCE_END =
            Pattern.compile("(?<=[。！？\\.\\!\\?])\\s*|\n+");

    private TextChunker() {}

    /** 固定窗口 + 重叠（原策略） */
    public static List<String> chunk(String text, int chunkSize, int overlap) {
        if (text == null) {
            return List.of();
        }
        String normalized = text.replace("\r\n", "\n").trim();
        if (normalized.isEmpty()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + chunkSize, normalized.length());
            chunks.add(normalized.substring(start, end));
            if (end >= normalized.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        return chunks;
    }

    /**
     * 语义分块：优先按段落合并，过长则按句切分，再在块间做尾部重叠，减少截断句子与孤立短语。
     */
    public static List<String> chunkSemantic(String text, int maxChunk, int overlap, int minChunk) {
        if (text == null) {
            return List.of();
        }
        String normalized = text.replace("\r\n", "\n").trim();
        if (normalized.isEmpty()) {
            return List.of();
        }
        String[] paras = PARA_SPLIT.split(normalized);
        List<String> mergedParas = mergeParagraphs(paras, maxChunk, minChunk);
        List<String> sized = new ArrayList<>();
        for (String block : mergedParas) {
            if (block.length() <= maxChunk) {
                sized.add(block);
            } else {
                sized.addAll(splitLongBlock(block, maxChunk));
            }
        }
        return applyTailOverlap(sized, overlap);
    }

    /** 相邻段落总长度不超过 maxChunk 时合并为一块（minChunk 保留给调用方调参，当前策略以 maxChunk 为硬上限）。 */
    private static List<String> mergeParagraphs(String[] paras, int maxChunk, @SuppressWarnings("unused") int minChunk) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String p : paras) {
            String t = p.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (cur.isEmpty()) {
                cur.append(t);
                continue;
            }
            int combined = cur.length() + 2 + t.length();
            if (combined <= maxChunk) {
                cur.append("\n\n").append(t);
            } else {
                flushParagraph(out, cur);
                cur = new StringBuilder(t);
            }
        }
        flushParagraph(out, cur);
        return out;
    }

    private static void flushParagraph(List<String> out, StringBuilder cur) {
        if (!cur.isEmpty()) {
            out.add(cur.toString());
            cur.setLength(0);
        }
    }

    private static List<String> splitLongBlock(String block, int maxChunk) {
        String[] parts = SENTENCE_END.split(block);
        List<String> sentences = new ArrayList<>();
        for (String s : parts) {
            String t = s.trim();
            if (!t.isEmpty()) {
                sentences.add(t);
            }
        }
        if (sentences.isEmpty()) {
            return hardSlice(block, maxChunk);
        }
        List<String> chunks = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String sent : sentences) {
            if (cur.isEmpty()) {
                cur.append(sent);
                continue;
            }
            if (cur.length() + 1 + sent.length() <= maxChunk) {
                cur.append(" ").append(sent);
            } else {
                if (cur.length() > maxChunk) {
                    chunks.addAll(hardSlice(cur.toString(), maxChunk));
                } else {
                    chunks.add(cur.toString());
                }
                cur = new StringBuilder(sent);
            }
        }
        if (!cur.isEmpty()) {
            if (cur.length() > maxChunk) {
                chunks.addAll(hardSlice(cur.toString(), maxChunk));
            } else {
                chunks.add(cur.toString());
            }
        }
        return chunks;
    }

    private static List<String> hardSlice(String text, int maxChunk) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxChunk, text.length());
            parts.add(text.substring(start, end));
            if (end >= text.length()) {
                break;
            }
            start = end;
        }
        return parts;
    }

    /** 将上一块末尾 overlap 字符拼到下一块前，增强跨块连贯性 */
    private static List<String> applyTailOverlap(List<String> chunks, int overlap) {
        if (chunks.isEmpty() || overlap <= 0) {
            return chunks;
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String piece = chunks.get(i);
            if (i > 0) {
                String prev = chunks.get(i - 1);
                String tail = prev.length() <= overlap ? prev : prev.substring(prev.length() - overlap);
                piece = tail + "\n" + piece;
            }
            out.add(piece);
        }
        return out;
    }
}

