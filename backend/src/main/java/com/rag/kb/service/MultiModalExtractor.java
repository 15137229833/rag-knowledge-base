package com.rag.kb.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多模态提取服务
 * 
 * 支持图片、表格、公式的解析和检索
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiModalExtractor {

    private static final Pattern LATEX_BLOCK_DOUBLE = Pattern.compile("\\$\\$(.+?)\\$\\$", Pattern.DOTALL);
    private static final Pattern LATEX_BRACKETS = Pattern.compile("\\\\\\[(.+?)\\\\\\]", Pattern.DOTALL);
    private static final Pattern LATEX_PARENS = Pattern.compile("\\\\\\((.+?)\\\\\\)", Pattern.DOTALL);
    private static final Pattern LATEX_INLINE_DOLLAR =
            Pattern.compile("(?<!\\$)\\$(?!\\$)([^$\\n]+?)\\$(?!\\$)");

    /**
     * 图片块数据类
     */
    public record ImageBlock(
            int pageNumber,
            byte[] imageData,
            String ocrText,
            String description
    ) {}

    /**
     * 表格块数据类
     */
    public record TableBlock(
            int pageNumber,
            String tableData,
            String markdownText,
            String description
    ) {}

    /**
     * 公式块数据类
     */
    public record FormulaBlock(
            int pageNumber,
            String latexFormula,
            String description
    ) {}

    /**
     * 提取图片（占位实现）
     * 
     * 实际实现需要：
     * - PDFBox 提取图片
     * - Tesseract OCR 识别文字
     * - 视觉模型生成描述
     */
    public List<ImageBlock> extractImages(byte[] pdfBytes) {
        log.info("提取图片（占位实现）");
        // TODO: 实现图片提取
        // 1. 使用 PDFBox 提取图片
        // 2. 使用 Tesseract OCR 识别文字
        // 3. 使用视觉模型生成描述
        return new ArrayList<>();
    }

    /**
     * 提取表格（占位实现）
     * 
     * 实际实现需要：
     * - Apache POI 提取 Word 表格
     * - Tabula 提取 PDF 表格
     * - 转换为 Markdown 格式
     */
    public List<TableBlock> extractTables(byte[] docBytes) {
        log.info("提取表格（占位实现）");
        // TODO: 实现表格提取
        // 1. 使用 Apache POI 提取 Word 表格
        // 2. 使用 Tabula 提取 PDF 表格
        // 3. 转换为 Markdown 格式
        return new ArrayList<>();
    }

    /**
     * 提取公式（占位实现）
     * 
     * 实际实现需要：
     * - 识别 LaTeX 公式
     * - 转换为可读描述
     * - 生成向量嵌入
     */
    public List<FormulaBlock> extractFormulas(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        int page = 0;
        List<FormulaBlock> raw = new ArrayList<>();
        collectFormulas(LATEX_BLOCK_DOUBLE, markdown, page, raw);
        collectFormulas(LATEX_BRACKETS, markdown, page, raw);
        collectFormulas(LATEX_PARENS, markdown, page, raw);
        collectFormulas(LATEX_INLINE_DOLLAR, markdown, page, raw);
        Set<String> seen = new LinkedHashSet<>();
        List<FormulaBlock> out = new ArrayList<>();
        for (FormulaBlock b : raw) {
            String k = b.latexFormula();
            if (!k.isBlank() && seen.add(k)) {
                out.add(b);
            }
        }
        return out;
    }

    private static void collectFormulas(Pattern p, String md, int page, List<FormulaBlock> out) {
        Matcher m = p.matcher(md);
        while (m.find()) {
            String latex = m.group(1).trim();
            if (latex.isEmpty()) {
                continue;
            }
            String desc = latex.length() > 160 ? latex.substring(0, 157) + "…" : latex;
            out.add(new FormulaBlock(page, latex, desc));
        }
    }

    /**
     * 生成图片描述（占位实现）
     * 
     * 实际实现需要：
     * - 调用视觉模型（如 BLIP, CLIP）
     * - 生成图片描述
     */
    public String describeImage(byte[] imageData) {
        log.info("生成图片描述（占位实现）");
        // TODO: 实现图片描述生成
        // 1. 调用视觉模型
        // 2. 生成图片描述
        return "图片描述占位";
    }

    /**
     * 识别图片中的文字（占位实现）
     * 
     * 实际实现需要：
     * - 调用 Tesseract OCR
     * - 识别图片中的文字
     */
    public String recognizeText(byte[] imageData) {
        log.info("识别图片文字（占位实现）");
        // TODO: 实现 OCR 文字识别
        // 1. 调用 Tesseract OCR
        // 2. 识别图片中的文字
        return "OCR 识别结果占位";
    }
}
