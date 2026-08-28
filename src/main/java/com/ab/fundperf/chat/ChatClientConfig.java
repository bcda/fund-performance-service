package com.ab.fundperf.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class ChatClientConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, ToolCallbackProvider fundReportTools) {
        return builder
                .defaultSystem("""
                You are a fund performance assistant. Answer only using the
                searchFundDocuments tool for facts about specific funds — never
                guess figures. If the requested fund isn't found, call
                listIngestedFunds and tell the user what is available.
                Cite the fund name for every claim you make.
                """)
                .defaultToolCallbacks(fundReportTools)
                .build();
    }

    @Bean
    ApplicationRunner logDiscoveredTools(ToolCallbackProvider fundReportTools) {
        return args -> {
            System.out.println("=== Tool discovery check running ===");
            var tools = fundReportTools.getToolCallbacks();
            System.out.println("Tool count: " + tools.length);
            Arrays.stream(tools).forEach(t -> System.out.println("Discovered tool: " + t.getToolDefinition().name()));
        };
    }
}