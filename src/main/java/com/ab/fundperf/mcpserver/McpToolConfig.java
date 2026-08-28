package com.ab.fundperf.mcpserver;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider toolCallbackProvider(FundReportTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}