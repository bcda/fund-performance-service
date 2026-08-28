package com.ab.fundperf.config;

import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.databind.ObjectMapper;

    @Configuration
    public class VectorStoreConfig {

        @Value("${spring.ai.vectorstore.chroma.client.host}")
        private String chromaHost;

        @Value("${spring.ai.vectorstore.chroma.client.port}")
        private int chromaPort;

        @Bean
        public ChromaApi chromaApi(ObjectMapper objectMapper) {
            return new ChromaApi(chromaHost + ":" + chromaPort, RestClient.builder(), objectMapper);
        }

        @Bean
        public VectorStore vectorStore(EmbeddingModel embeddingModel, ChromaApi chromaApi) {
            return ChromaVectorStore.builder(chromaApi, embeddingModel)
                    .collectionName("fund_reports")
                    .databaseName("default_database")
                    .tenantName("default_tenant")
                    .initializeSchema(true)
                    .build();
        }
}