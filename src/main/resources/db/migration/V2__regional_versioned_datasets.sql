-- CoolingVerse 지역 자연키 및 무중단 데이터 버전 전환
-- 기존 운영 판교·부천 데이터는 지역별 2025년 legacy 실행으로 보존한다.

ALTER TABLE districts ADD (
  region_code VARCHAR2(30), display_name VARCHAR2(100), is_active CHAR(1) DEFAULT 'N'
);
UPDATE districts SET region_code = 'pangyo', display_name = '판교', is_active = 'Y' WHERE district_id = 1;
UPDATE districts SET region_code = 'ingye', display_name = '수원 인계동', is_active = 'N' WHERE district_id = 2;
UPDATE districts SET region_code = 'bucheon', display_name = '부천', is_active = 'Y' WHERE district_id = 3;
MERGE INTO districts d USING (SELECT 'bucheon' region_code FROM dual) s ON (d.region_code = s.region_code)
WHEN NOT MATCHED THEN INSERT
  (name, region_code, display_name, is_active, sido, sigungu, center_lat, center_lng, is_base)
VALUES ('부천시', 'bucheon', '부천', 'Y', '경기도', '부천시', 37.5034, 126.7660, 'N');
ALTER TABLE districts MODIFY (region_code NOT NULL, display_name NOT NULL, is_active NOT NULL);
ALTER TABLE districts ADD CONSTRAINT uq_district_region UNIQUE (region_code);
ALTER TABLE districts ADD CONSTRAINT ck_district_active CHECK (is_active IN ('Y', 'N'));

ALTER TABLE grids ADD (region_code VARCHAR2(30));
UPDATE grids g SET region_code = (SELECT d.region_code FROM districts d WHERE d.district_id = g.district_id);
ALTER TABLE grids MODIFY (region_code NOT NULL);
ALTER TABLE grids ADD CONSTRAINT fk_grid_region FOREIGN KEY (region_code) REFERENCES districts(region_code);
ALTER TABLE grids ADD CONSTRAINT uq_grid_region_id UNIQUE (region_code, grid_id);

CREATE TABLE data_pipeline_runs (
  pipeline_run_id VARCHAR2(80) NOT NULL, region_code VARCHAR2(30) NOT NULL,
  analysis_year NUMBER(4) NOT NULL, status VARCHAR2(20) NOT NULL,
  input_version VARCHAR2(200) NOT NULL, input_manifest_sha256 VARCHAR2(64),
  quality_report CLOB, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  activated_at TIMESTAMP,
  CONSTRAINT pk_pipeline_runs PRIMARY KEY (pipeline_run_id),
  CONSTRAINT fk_run_region FOREIGN KEY (region_code) REFERENCES districts(region_code),
  CONSTRAINT ck_run_status CHECK (status IN ('STAGING','VALIDATED','ACTIVE','FAILED','ROLLED_BACK'))
);
CREATE TABLE active_dataset_versions (
  region_code VARCHAR2(30) NOT NULL, analysis_year NUMBER(4) NOT NULL,
  active_run_id VARCHAR2(80) NOT NULL, previous_run_id VARCHAR2(80),
  activated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT pk_active_dataset PRIMARY KEY (region_code, analysis_year),
  CONSTRAINT fk_active_region FOREIGN KEY (region_code) REFERENCES districts(region_code),
  CONSTRAINT fk_active_run FOREIGN KEY (active_run_id) REFERENCES data_pipeline_runs(pipeline_run_id),
  CONSTRAINT fk_previous_run FOREIGN KEY (previous_run_id) REFERENCES data_pipeline_runs(pipeline_run_id)
);
INSERT INTO data_pipeline_runs
  (pipeline_run_id, region_code, analysis_year, status, input_version, quality_report, activated_at)
VALUES ('legacy-pangyo-2025','pangyo',2025,'ACTIVE','legacy-production','{"migration":"V2"}',CURRENT_TIMESTAMP);
INSERT INTO data_pipeline_runs
  (pipeline_run_id, region_code, analysis_year, status, input_version, quality_report, activated_at)
VALUES ('legacy-bucheon-2025','bucheon',2025,'ACTIVE','legacy-production','{"migration":"V2"}',CURRENT_TIMESTAMP);
INSERT INTO active_dataset_versions (region_code, analysis_year, active_run_id)
VALUES ('pangyo', 2025, 'legacy-pangyo-2025');
INSERT INTO active_dataset_versions (region_code, analysis_year, active_run_id)
VALUES ('bucheon', 2025, 'legacy-bucheon-2025');

ALTER TABLE apartments ADD (region_code VARCHAR2(30), pipeline_run_id VARCHAR2(80));
UPDATE apartments a SET region_code = NVL((SELECT g.region_code FROM grids g WHERE g.grid_id = a.grid_id),'pangyo');
UPDATE apartments SET pipeline_run_id = CASE region_code
  WHEN 'bucheon' THEN 'legacy-bucheon-2025' ELSE 'legacy-pangyo-2025' END;
ALTER TABLE apartments MODIFY (region_code NOT NULL, pipeline_run_id NOT NULL);
ALTER TABLE apartments ADD CONSTRAINT fk_apt_region FOREIGN KEY (region_code) REFERENCES districts(region_code);
ALTER TABLE apartments ADD CONSTRAINT fk_apt_run FOREIGN KEY (pipeline_run_id) REFERENCES data_pipeline_runs(pipeline_run_id);

ALTER TABLE enforcement ADD (region_code VARCHAR2(30), pipeline_run_id VARCHAR2(80));
UPDATE enforcement e SET region_code = NVL((SELECT g.region_code FROM grids g WHERE g.grid_id = e.grid_id),'pangyo');
UPDATE enforcement SET pipeline_run_id = CASE region_code
  WHEN 'bucheon' THEN 'legacy-bucheon-2025' ELSE 'legacy-pangyo-2025' END;
ALTER TABLE enforcement MODIFY (region_code NOT NULL, pipeline_run_id NOT NULL);
ALTER TABLE enforcement ADD CONSTRAINT fk_enf_region FOREIGN KEY (region_code) REFERENCES districts(region_code);
ALTER TABLE enforcement ADD CONSTRAINT fk_enf_run FOREIGN KEY (pipeline_run_id) REFERENCES data_pipeline_runs(pipeline_run_id);

ALTER TABLE air_quality ADD (region_code VARCHAR2(30), pipeline_run_id VARCHAR2(80));
UPDATE air_quality a SET region_code = NVL((SELECT g.region_code FROM grids g WHERE g.grid_id = a.grid_id),'pangyo');
UPDATE air_quality SET pipeline_run_id = CASE region_code
  WHEN 'bucheon' THEN 'legacy-bucheon-2025' ELSE 'legacy-pangyo-2025' END;
ALTER TABLE air_quality MODIFY (region_code NOT NULL, pipeline_run_id NOT NULL);
ALTER TABLE air_quality ADD CONSTRAINT fk_air_region FOREIGN KEY (region_code) REFERENCES districts(region_code);
ALTER TABLE air_quality ADD CONSTRAINT fk_air_run FOREIGN KEY (pipeline_run_id) REFERENCES data_pipeline_runs(pipeline_run_id);

ALTER TABLE risk_index ADD (
  region_code VARCHAR2(30), pipeline_run_id VARCHAR2(80),
  analysis_year NUMBER(4), analysis_month NUMBER(2)
);
UPDATE risk_index r SET region_code = (SELECT g.region_code FROM grids g WHERE g.grid_id = r.grid_id),
  analysis_year = 2025, analysis_month = 10;
UPDATE risk_index SET pipeline_run_id = CASE region_code
  WHEN 'bucheon' THEN 'legacy-bucheon-2025' ELSE 'legacy-pangyo-2025' END;
ALTER TABLE risk_index MODIFY (region_code NOT NULL, pipeline_run_id NOT NULL, analysis_year NOT NULL, analysis_month NOT NULL);
BEGIN EXECUTE IMMEDIATE 'ALTER TABLE risk_index DROP CONSTRAINT fk_risk_grid';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -2443 THEN RAISE; END IF; END;
/
BEGIN EXECUTE IMMEDIATE 'ALTER TABLE risk_index DROP CONSTRAINT uq_risk';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -2443 THEN RAISE; END IF; END;
/
ALTER TABLE risk_index ADD CONSTRAINT fk_risk_region_grid
  FOREIGN KEY (region_code, grid_id) REFERENCES grids(region_code, grid_id);
ALTER TABLE risk_index ADD CONSTRAINT fk_risk_run FOREIGN KEY (pipeline_run_id) REFERENCES data_pipeline_runs(pipeline_run_id);
ALTER TABLE risk_index ADD CONSTRAINT uq_risk_version UNIQUE
  (pipeline_run_id, region_code, grid_id, analysis_year, analysis_month, hour_of_day);
ALTER TABLE risk_index ADD CONSTRAINT ck_risk_month CHECK (analysis_month BETWEEN 1 AND 12);
ALTER TABLE risk_index ADD CONSTRAINT ck_risk_hour CHECK (hour_of_day BETWEEN 0 AND 23);

ALTER TABLE scenarios ADD (analysis_month NUMBER(2) DEFAULT 10);
UPDATE scenarios SET analysis_month = 10 WHERE analysis_month IS NULL;
ALTER TABLE scenarios MODIFY (analysis_month NOT NULL);
ALTER TABLE scenarios ADD CONSTRAINT ck_scenario_month CHECK (analysis_month BETWEEN 1 AND 12);
COMMIT;
