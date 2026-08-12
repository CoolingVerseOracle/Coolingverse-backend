package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class FlywayMigrationContractTest {
    @Test
    void regionalMigrationContainsDatabaseIsolationContracts() throws Exception {
        try (var stream = getClass().getResourceAsStream("/db/migration/V2__regional_versioned_datasets.sql")) {
            assertTrue(stream != null, "Flyway V2 migration must exist");
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
            assertTrue(sql.contains("uq_district_region unique (region_code)"));
            assertTrue(sql.contains("uq_grid_region_id unique (region_code, grid_id)"));
            assertTrue(sql.contains("foreign key (region_code, grid_id) references grids(region_code, grid_id)"));
            assertTrue(sql.contains("pipeline_run_id, region_code, grid_id, analysis_year, analysis_month, hour_of_day"));
            assertTrue(sql.contains("active_dataset_versions"));
            assertTrue(sql.contains("set region_code = 'bucheon'"));
            assertTrue(sql.contains("legacy-bucheon-2025"));
            assertTrue(sql.contains("select g.region_code from grids g where g.grid_id = r.grid_id"));
        }
    }
}
