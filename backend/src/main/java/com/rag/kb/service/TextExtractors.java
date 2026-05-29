package com.rag.kb.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class TextExtractors {

    private TextExtractors() {}

    /** 是否为知识库支持的图片类型（与上传白名单一致） */
    public static boolean isImageFile(String filename, String contentType) {
        String lower = filename == null ? "" : filename.toLowerCase();
        boolean byExt =
                lower.endsWith(".png")
                        || lower.endsWith(".jpg")
                        || lower.endsWith(".jpeg")
                        || lower.endsWith(".webp")
                        || lower.endsWith(".gif");
        boolean byCt = contentType != null && contentType.toLowerCase().startsWith("image/");
        return byExt || byCt;
    }

    /** 是否为知识库支持的视频类型 */
    public static boolean isVideoFile(String filename, String contentType) {
        String lower = filename == null ? "" : filename.toLowerCase();
        boolean byExt =
                lower.endsWith(".mp4")
                        || lower.endsWith(".mov")
                        || lower.endsWith(".m4v")
                        || lower.endsWith(".webm")
                        || lower.endsWith(".avi");
        boolean byCt = contentType != null && contentType.toLowerCase().startsWith("video/");
        return byExt || byCt;
    }

    public static List<PageText> extractPages(byte[] bytes, String filename, String contentType) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return extractPdf(bytes);
        }
        if (lower.endsWith(".docx")) {
            return extractDocx(bytes);
        }
        if (lower.endsWith(".md") || lower.endsWith(".txt") || lower.endsWith(".markdown")) {
            String t = new String(bytes, StandardCharsets.UTF_8);
            return List.of(new PageText(null, t));
        }
        throw new ResponseStatusException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "暂不支持的文件类型，请上传 pdf / docx / md / txt / 图片 / 视频");
    }

    private static List<PageText> extractPdf(byte[] bytes) {
        List<PageText> pages = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            int n = doc.getNumberOfPages();
            for (int i = 1; i <= n; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(doc);
                pages.add(new PageText(i, text == null ? "" : text));
            }
        } catch (Exception e) {
            throw new IllegalStateException("PDF 解析失败: " + e.getMessage(), e);
        }
        return pages;
    }

    private static List<PageText> extractDocx(byte[] bytes) {
        try (XWPFDocument doc = new XWPFDocument(new java.io.ByteArrayInputStream(bytes));
                XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
            String text = ex.getText();
            return List.of(new PageText(null, text == null ? "" : text));
        } catch (Exception e) {
            throw new IllegalStateException("Word 解析失败: " + e.getMessage(), e);
        }
    }

    @Getter
    @RequiredArgsConstructor
    public static class PageText {
        private final Integer pageNo;
        private final String text;
    }
}
