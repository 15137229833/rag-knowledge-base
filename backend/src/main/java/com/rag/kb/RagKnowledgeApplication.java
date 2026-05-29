package com.rag.kb;

import com.rag.kb.config.MultimodalProperties;
import com.rag.kb.config.RagRetrievalProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(excludeName = {
        "org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration",
        "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration"
})
@EnableAsync
@EnableConfigurationProperties({RagRetrievalProperties.class, MultimodalProperties.class})
public class RagKnowledgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagKnowledgeApplication.class, args);
    }
}
