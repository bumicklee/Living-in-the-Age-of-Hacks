"""15-3 최종 점수판: 취약 37개 → 0개 · 점검표 74행(F01~F48 · E01~E26).

시작 지점(15-2 완료): course/audit.yaml의 「처리 결과」·「근거」 칸이 비어 있다 → 레드.
완료 지점: ① 74행 모두 처리 결과(고침·완화·수용·확인 필요)와 근거, 수용은 다시 볼 날 ② 「고침」 행의 다시 확인 방법이 그린(재연·시험 파일·설정)
정상 시험(시작 지점에서도 그린): 지금 지점의 점수판이 취약 0 / 재연 37(15-2까지 모든 정답 적용).
시작 지점 37과의 비교는 make final-score가 한다(앱을 두 번 다시 빌드해야 해서 시험에 넣지 않는다). 결과는 reports/final-check/.
"""
import pytest

from daon import audit


def test_audit_sheet_has_a_result_for_every_row():
    sheet = audit.rows()
    probs = audit.problems(sheet)
    assert not probs, f"기대: 74행 모두 처리 결과·근거(수용은 다시 볼 날). 문제 {len(probs)}개, 예: {probs[:5]}"


def test_fixed_rows_are_confirmed(stack):
    sheet = [r for r in audit.rows() if r.get("result") == "고침"]
    assert len(sheet) >= 40, f"기대: 「고침」 행이 충분히 채워짐(지금 {len(sheet)}행)"
    bad = []
    for r in sheet:
        ok, note = audit.check_row(r, stack)
        if ok is False:
            bad.append(f"{r['id']}: {note}")
    assert not bad, f"기대: 「고침」 행은 다시 확인이 그린. 레드: {bad[:6]}"


@pytest.mark.normal
def test_scoreboard_is_zero_of_37(stack):
    import replay
    from daon.cli import TOTAL_T
    mods = replay.all_modules()
    assert len(mods) == TOTAL_T == 37, f"기대: 재연 37개(T01~T37). 실제 {len(mods)}"
    red = []
    for t, mod in mods.items():
        code = mod.run.__code__
        kw = {"dynamic_run": False} if "dynamic_run" in code.co_varnames[:code.co_argcount] else {}
        if mod.run(stack, **kw).vulnerable:
            red.append(t)
    assert not red, f"기대: 최종 지점 취약 0 / 37. 레드: {red}"
