# 4개 지역 앵커 표 산출 — 분석팀 승인 방식(#30 코멘트, 2026-08-16)
#  ① RISK_BEFORE = 지역 risk_index 평균
#  ② 100% 개방 시 최대 감소폭 = 고정 스케일 방식 (open_count 스케일 유지, 미개방 유휴면 전량 추가)
#  ③ 참여율별 감소폭 = 최대 감소폭 × 참여율 (선형)
#  ④ 탄소 = ADDED_SUPPLY × 0.306 × 1.2 (12h 기준) — 백엔드는 운영시간 기반 기존 수식 유지
import json

import pandas as pd

RATES = [10, 30, 50, 70, 100]


def load(p):
    for enc in ['utf-8-sig', 'cp949', 'utf-8']:
        try:
            # 평촌부터는 데이터 파이프라인 계약 CSV(grid_code)라 기존 컬럼명으로 맞춘다
            return pd.read_csv(p, encoding=enc).rename(columns={'grid_code': 'grid_id'})
        except Exception:
            pass


def min_max(s):
    lo, hi = s.min(), s.max()
    return s * 0.0 if hi == lo else (s - lo) / (hi - lo + 1e-6)


def max_delta_fixed(risk, apt):
    grids = risk.grid_id.unique()
    per_grid = risk.groupby('grid_id').first()
    base_open = apt.groupby('grid_id')['open_count'].sum().reindex(grids).fillna(0.0)
    add_pool = apt[apt.is_open == 'N'].groupby('grid_id')['open_count'].sum().reindex(grids).fillna(0.0)
    # 재현 확인: 현재 supply_shortage
    repro = (1.0 - min_max(base_open)).reindex(per_grid.index)
    err = (repro - per_grid.supply_shortage).abs().max()
    lo, hi = base_open.min(), base_open.max()
    new_open = base_open + add_pool
    new_supply = (1.0 - ((new_open - lo) / (hi - lo + 1e-6)).clip(0, 1)).reindex(per_grid.index)
    delta_supply = (per_grid.supply_shortage - new_supply).mean()
    return 0.35 * delta_supply * 100, err


B = r'C:\Users\skw01\Documents\parking-erd'
S = r'C:\git_torii\Coolingverse-backend'
P = r'C:\git_torii\data\Anyang Pyeongchon\build_output'
regions = {
    'pangyo': (rf'{B}\bundang_final\adb-upload\risk_index_v2.csv', rf'{B}\adb-upload\apartments.csv'),
    'bucheon': (rf'{B}\bucheon\bucheon_risk_index_final.csv', rf'{B}\bucheon\bucheon_apartments_erd.csv'),
    'sanbon': (rf'{S}\Sanbon\pipeline\sanbon_risk_index_final.csv', rf'{S}\Sanbon\pipeline\sanbon_apartments_erd.csv'),
    'ilsan': (rf'{S}\ilsan\ilsan_risk_index.csv', rf'{S}\ilsan\goyang_ilsan_apartments_erd.csv'),
    # 평촌은 Coolingverse-data 파이프라인 build_output(계약 CSV)을 그대로 쓴다
    'pyeongchon': (rf'{P}\risk_index.csv', rf'{P}\apartments.csv'),
}

result = {}
for code, (rp, ap) in regions.items():
    risk, apt = load(rp), load(ap)
    before = risk.risk_score.mean()
    mx, err = max_delta_fixed(risk, apt)
    unopened = int(apt[apt.is_open == 'N'].open_count.sum())
    deltas = [round(mx * r / 100, 2) for r in RATES]
    result[code] = {'baseline': round(before, 2), 'max_delta': round(mx, 4), 'deltas': deltas,
                    'idle_unopened': unopened, 'supply_repro_err': round(float(err), 6)}
    rows = ' / '.join(f'{r}%→-{d}' for r, d in zip(RATES, deltas))
    print(f'{code:8s} baseline {before:6.2f}  최대감소 {mx:.4f}  재현오차 {err:.4f}  미개방유휴면 {unopened:,}')
    print(f'         앵커: {rows}')

with open(rf'{S}\Sanbon\pipeline\region_anchors.json', 'w', encoding='utf-8') as f:
    json.dump(result, f, ensure_ascii=False, indent=2)
print('\n저장: region_anchors.json')
