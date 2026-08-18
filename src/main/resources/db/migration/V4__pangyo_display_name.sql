-- 판교 표시명을 실제 분석 범위에 맞춰 정정한다.
-- 분석 대상은 성남시 분당구 전체이고 판교는 그 안의 일부 지역이라, 기존 표시명이 범위를 좁게 오인시켰다.
-- region_code('pangyo')는 DB 자연키라 그대로 두고 사람이 보는 이름만 바꾼다.
--
-- 시나리오 목록의 표시명은 ScenarioStore가 조회 시점에 Regions enum에서 파생하므로
-- 저장된 행을 손댈 필요가 없다. 여기서는 districts 쪽 표기만 맞춘다.
UPDATE districts SET display_name = '성남 분당' WHERE region_code = 'pangyo';
