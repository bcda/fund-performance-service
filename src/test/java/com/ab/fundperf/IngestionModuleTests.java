package com.ab.fundperf;

import com.ab.fundperf.ingestion.DocumentIngestionService;
import com.ab.fundperf.ingestion.FundIngestedEvent;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.PublishedEvents;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled
@ApplicationModuleTest
class IngestionModuleTests {

    @Autowired
    DocumentIngestionService ingestionService;

    @Test
    void ingestingPublishesEvent(PublishedEvents events) {
        // ... ingest a sample PDF ...
        assertThat(events.ofType(FundIngestedEvent.class)).hasSize(1);
    }
}