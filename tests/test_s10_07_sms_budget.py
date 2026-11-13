"""10-7 비용 고갈: SMS 펌핑 · T27(PART=sms). LLM·서버리스는 그림 설명(결정 #14, 랩 재연 없음).

시작 지점: 로그인 없는 GET /sso/getAuthCode에 상한이 없다(가짜 번호 50개 → 문자 50통), 같은 번호 연속 요청도 보내고,
          국외 번호도 문자 사업자에 그대로 넘긴다 → 레드.
완료 지점: ① 50건 중 20건에서 멈춤(전체 시간당 예산) ② 같은 번호 60초 안 두 번째 429 ③ 국외 번호는 스텁을 부르기 전에 400
          ④ budget.tripped.global 한 줄 ⑤ budget.denied.sms에 전화번호 원문 없음.
정상 시험: 예산이 남은 상태에서 인증 문자 1건은 그대로 간다. 시험마다 랩 카운터(rl:*, 예산 포함)를 먼저 비운다.
"""
import httpx
import pytest

from daon.config import URLS
from replay import _logs
from replay import _money as m
from replay import t27_abuse as t27


def ask(phone: str) -> int:
    return httpx.get(URLS["spring"]["portal"] + "/sso/getAuthCode", params={"telephone": phone}, timeout=10).status_code


@pytest.fixture(autouse=True)
def fresh_budget():
    m.clear_counters()
    yield
    m.clear_counters()           # 다음 시험(4-5 재설정 문자 등)이 멈춘 예산을 물려받지 않게


def test_global_budget_stops_at_20():
    since = _logs.now()
    s = t27.sms_scene()
    assert s["sent"] <= t27.SMS_BUDGET, f"기대: 가짜 번호 50개 중 문자는 {t27.SMS_BUDGET}통까지. 실제 {s['sent']}통"
    e = _logs.wait_event("mall-portal", since, "budget.tripped.global", "10-7", timeout=8)
    assert e and e.get("actor") == "system:sms", f"기대: SECURITY budget.tripped.global. 실제 {e}"


def test_same_number_twice_in_60s():
    since = _logs.now()
    first, second = ask("010-0000-0077"), ask("010-0000-0077")
    assert first == 200 and second == 429, f"기대: 같은 번호 60초 안 두 번째는 429. 실제 {first}, {second}"
    e = _logs.wait_event("mall-portal", since, "budget.denied.sms", "10-7", timeout=8)
    assert e and "0077" not in str(e) and str(e.get("target", "")).startswith("phone:"), f"기대: target phone:<해시 8자>, 번호 원문 없음. 실제 {e}"


def test_foreign_number_rejected_before_stub():
    before = m.sms_stats()["rejected_format"]
    code = ask(t27.SMS_FOREIGN)
    after = m.sms_stats()["rejected_format"]
    assert code == 400, f"기대: 국외 번호는 앱이 400. 실제 {code}"
    assert after == before, f"기대: 문자 사업자(스텁)를 부르지 않음. 스텁 형식 거절 {before} → {after}"


@pytest.mark.normal
def test_one_code_is_sent():
    n0 = m.sms_stats()["messages"]
    assert ask("010-0000-0088") == 200, "기대: 인증 문자 요청 1건은 200"
    assert m.sms_stats()["messages"] == n0 + 1, "기대: 가짜 문자 스텁에 1통"
