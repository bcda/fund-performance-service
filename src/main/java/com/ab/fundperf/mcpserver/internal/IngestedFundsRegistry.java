package com.ab.fundperf.mcpserver.internal;

import com.ab.fundperf.ingestion.FundIngestedEvent;
import org.springframework.modulith.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class IngestedFundsRegistry {

    private final Set<String> funds = ConcurrentHashMap.newKeySet();

    @ApplicationModuleListener
    void on(FundIngestedEvent event) {
        funds.add(event.fundName());
    }

    public List<String> getAll() { return List.copyOf(funds); }
    void add(String fundName) { funds.add(fundName); }
}