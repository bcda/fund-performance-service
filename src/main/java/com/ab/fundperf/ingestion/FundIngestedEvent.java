package com.ab.fundperf.ingestion;

import java.time.Instant;

public record FundIngestedEvent(String fundName, int chunksIndexed, Instant at) {}
