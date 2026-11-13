"""15-1 캡스톤 ①: 다온마켓 연쇄 사고, 탐지 · 종합(2~14장 결과물을 쓴다). 가상 시나리오, 127.0.0.1의 랩, 가짜 값.

시작 지점(14장 완료 + 15장 시작 모습): 수강생 타임라인 docs/incident/capstone/timeline.md가 없다 → 레드.
완료 지점: 「핵심 칸」 표가 make capstone 정답 표(.daon/capstone/main/answer.json)와 같다.
  침입 계정 · 대량 조회 건수 · 환불 시도 건수·결과 · 바뀐 주문 수 · trace_id 셋은 그대로 비교한다.
  유출 커밋·유출 시작 시각·사고 뒤 정상 변경 주문은 실행마다 달라, 값을 적었으면 비교(시각은 ±3분)하고
  정답 견본처럼 「실행마다 다름」이라고 적었으면 넘어간다.
정상 시험(랩 도구라 시작 지점에서도 그린): 시나리오 재생이 정답 표의 「막힘·탐지만·놓침」과 같은 결과를 낸다.
사고가 아직 없으면 시험이 make capstone PART=1을 한 번 돌린다(수강생 사고가 이미 있으면 그대로 쓴다. 지우지 않는다).
"""
import re
from datetime import datetime, timezone

import pytest

from daon import capstone as cap
from daon.config import ROOT
from replay import _obs

DOC = ROOT / "docs" / "incident" / "capstone" / "timeline.md"
STRICT = ("침입 계정", "대량 조회 건수", "환불 시도 건수", "환불 결과", "바뀐 주문 수", "trace_id 셋")
LOOSE = ("유출 커밋", "유출 시작 시각", "사고 뒤 정상 변경 주문")
MARK = "실행마다 다름"


@pytest.fixture(scope="module")
def answer():
    ans = cap.load(cap.MAIN)
    if not ans or not cap.actor_exists(cap.MAIN):
        ans = cap.run(cap.MAIN, echo=lambda *_: None, fresh=True)
    return ans


def core_cells(text: str) -> dict:
    part = text.split("## 핵심 칸", 1)[-1].split("\n## ", 1)[0]
    out = {}
    for line in part.splitlines():
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) == 2 and cells[0] not in ("칸", "---"):
            out[cells[0]] = cells[1]
    return out


def want(ans: dict) -> dict:
    blocked = sum(1 for v in ans["refund"].values() if v["approval"] in ("DENIED", "PENDING_SECOND") and v["paid"] == 0)
    return {"침입 계정": ans["actor"], "대량 조회 건수": str(ans["bulk"]["rows"]), "환불 시도 건수": str(len(ans["refund"])),
            "환불 결과": f"막힘 {blocked}", "바뀐 주문 수": str(len(ans["changed"])), "trace_id 셋": ", ".join(ans["traces3"])}


def _norm(v: str) -> str:
    return re.sub(r"\s+", " ", v.replace("`", "")).strip()


def _when(v: str) -> datetime | None:
    try:
        return datetime.fromisoformat(v.strip().replace("Z", "+00:00")).astimezone(timezone.utc)
    except ValueError:
        return None


def test_timeline_core_cells_match_answer(answer):
    assert DOC.exists(), f"기대: 수강생 타임라인 {DOC.relative_to(ROOT)}(양식 docs/templates/캡스톤-타임라인.md)"
    got = core_cells(DOC.read_text("utf-8"))
    missing = [k for k in STRICT + LOOSE if not got.get(k)]
    assert not missing, f"기대: 핵심 칸이 모두 채워짐. 빈 칸 {missing}"
    wrong = {k: (got[k], v) for k, v in want(answer).items() if _norm(got[k]) != _norm(v)}
    assert not wrong, f"기대: 정답 표와 같은 값. 다른 칸(적은 값, 정답) {wrong}"
    leak = answer["leak"]
    if MARK not in got["유출 커밋"]:
        assert got["유출 커밋"].strip()[:12] == leak["commit"], f"기대: 유출 커밋 {leak['commit']}(Gitea 커밋 앞 12자). 실제 {got['유출 커밋']}"
    if MARK not in got["유출 시작 시각"]:
        t, ref = _when(got["유출 시작 시각"]), _when(leak["at"])
        assert t and ref and abs((t - ref).total_seconds()) <= 180, f"기대: 유출 시작 시각 ≈ {leak['at']}(±3분). 실제 {got['유출 시작 시각']}"
    if MARK not in got["사고 뒤 정상 변경 주문"]:
        assert str(answer["legit_after"]["order"]) in got["사고 뒤 정상 변경 주문"], "기대: 사고 뒤 고객 요청으로 다시 바뀐 주문 번호"


@pytest.mark.normal
def test_replay_matches_answer_table(answer):
    table = {row["단계"]: row["결과"] for row in answer["table"]}
    assert table["5 남은 금액 초과 환불"] == "막힘" and table["5 고액 환불"] == "막힘", f"기대: 환불 두 시도 모두 막힘(13-3·13-4). 실제 {table}"
    assert table["6 권한 상승 없는 고위험 기능"] == "막힘" and table["6 닫힌 공용 계정"] == "막힘", f"기대: 권한 올리기 막힘(13-6). 실제 {table}"
    assert table["1 시크릿 유출"] == "탐지만", "기대: CI 시크릿 스캔(6-2 규칙)이 유출 설정 파일을 찾음"
    assert len(answer["changed"]) == cap.CHANGES and answer["bulk"]["rows"] > 0, f"기대: 정상 권한 행위(조회·수정)는 그대로 됨. 실제 {answer['bulk']} {len(answer['changed'])}"
    assert answer["login"]["ip_seen"] != cap.OFFICE_IP, "기대: 로그인 기록의 IP가 평소(가짜 사무실)와 다름"


@pytest.mark.normal
def test_audit_log_counts_changed_orders(answer):
    recs = [r for r in _obs.audit_lines() if r.get("actor") == answer["actor"] and r.get("action") == "change.order.receiver" and r.get("result") == "ok"]
    targets = {r["target"] for r in recs}
    assert {f"order:{o}" for o in answer["changed"]} <= targets, f"기대: 바뀐 주문 {len(answer['changed'])}건이 모두 감사 로그(target order:<ID>)에. 실제 {len(targets)}건"
    assert all("010-" not in str(r) for r in recs), "기대: 감사 로그에 바뀐 값(전화번호)이 없다"


@pytest.mark.normal
def test_one_alert_mail_arrives(answer):
    if not _obs.a05_on():
        pytest.fail("준비 안 됨: A5(경보)가 꺼져 있다", pytrace=False)
    started = _when(answer["started"]).timestamp()
    mail = _obs.wait_mail("data.read.bulk", since=started - 60, timeout=float(__import__("os").environ.get("DAON_ALERT_WAIT", "150")))
    assert mail, "기대: 대량 조회 경보 메일 한 통(8-4, Mailpit)"
