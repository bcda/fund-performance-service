package com.ab.fundperf;

import org.springframework.ai.vectorstore.chroma.autoconfigure.ChromaVectorStoreAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = { ChromaVectorStoreAutoConfiguration.class })
public class FundPerformanceServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(FundPerformanceServiceApplication.class, args);
	}

}
