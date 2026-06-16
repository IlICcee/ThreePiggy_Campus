package com.example.config;

import com.example.tool.CampusTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class AiConfig {

    // DashScope text-embedding-v2 向量维度
    private static final int EMBEDDING_DIMENSIONS = 1536;

    @Bean
    public ChatClient chatClient(ChatModel chatModel, CampusTool campusTool) {
        return ChatClient.builder(chatModel)
                .defaultTools(campusTool)
                .build();
    }

    /**
     * PostgreSQL + pgvector 向量存储
     * 表名: vector_store，新建时自动创建
     */
    @Bean
    public PgVectorStore pgVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(EMBEDDING_DIMENSIONS)
                .distanceType(PgDistanceType.COSINE_DISTANCE)
                .initializeSchema(true)
                .build();
    }
}
