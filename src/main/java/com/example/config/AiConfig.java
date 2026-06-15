package com.example.config;

import com.example.tool.CampusTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatModel chatModel, CampusTool campusTool) {
        return ChatClient.builder(chatModel)
                .defaultTools(campusTool)   // 注册 Tool
                .build();
    }
}