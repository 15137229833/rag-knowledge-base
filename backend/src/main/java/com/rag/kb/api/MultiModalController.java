package com.rag.kb.api;

import com.rag.kb.service.MultiModalExtractor;
import com.rag.kb.service.MultiModalExtractor.FormulaBlock;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tools")
@RequiredArgsConstructor
@Tag(name = "Multi-modal tools", description = "多模态辅助工具")
public class MultiModalController {

    private final MultiModalExtractor multiModalExtractor;

    public record FormulasRequest(String markdown) {}

    @PostMapping("/formulas")
    @Operation(summary = "从 Markdown 文本中提取 LaTeX 公式片段")
    public List<FormulaBlock> extractFormulas(@RequestBody FormulasRequest body) {
        return multiModalExtractor.extractFormulas(body == null ? null : body.markdown());
    }
}
