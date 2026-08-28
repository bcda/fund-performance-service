package com.ab.fundperf;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTests {

    ApplicationModules modules = ApplicationModules.of(FundPerformanceServiceApplication.class);

    @Test
    void verifyModuleStructure() {
        modules.verify();
    }

    @Test
    void writeDocumentation() {
        new Documenter(modules).writeDocumentation();   // generates PlantUML diagrams under target/spring-modulith-docs
    }
}