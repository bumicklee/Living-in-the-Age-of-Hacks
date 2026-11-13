"""14-3 회귀 재연(T 번호 없음, 점수판에 세지 않음) · 결제사가 느려졌을 뿐인데 상품 목록까지 멈춘다. make replay R=14-3

결제사 스텁을 느림 모드(3초, 오류 아님)로 두고, alice가 결제 확인(GET /pay/status) 40개를 동시에 보내는 동안
결제와 상관없는 상품 목록(GET /product/search)이 기준 시간(1.5초) 안에 오는지 잰다. 끝나면 스텁을 정상으로 되돌린다.
  레드: 상품 목록이 기준 시간을 넘김(요청 처리 스레드 24개가 결제사 응답을 기다리느라 모두 묶임)
  그린: 상품 목록이 기준 시간 안(결제 확인은 동시 6자리까지만 기다리고 나머지는 바로 「확인 중」)
9-5와의 차이: 9-5는 「오류 → 재시도 폭주」, 오늘은 「오류 없는 느림 → 자원 고갈」.
"""
from __future__ import annotations

from replay import NotReady, Result
from replay import _incident as inc

T = "R14-3"
TITLE = "결제사가 느려지면 상품 목록까지 멈춤(공유 자원 고갈, 회귀 재연)"
LESSON = "14-3"
SOURCE = "[교육용 추가] 14장 시작 모습의 결제 확인 API(결제사를 5초까지 기다림) + [랩 크기] 고객 앱 요청 처리 스레드 24개"
STACKS = ("spring",)


def run(stack: str = "spring") -> Result:
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 아직 없습니다(Express는 diffs/s14-03의 세마포어).")
    m = inc.slow_dependency()
    ev = [f"결제사 느림 모드 {m['slow_ms']}ms · 결제 확인 {m['concurrent']}개 동시(alice, 주문 {inc.STATUS_ORDER})",
          f"상품 목록 응답 시간(초): {m['product_seconds']} · HTTP {m['product_codes']} · 기준 {m['limit']}초",
          "결제 확인 결과(HTTP:상태 → 개수): " + ", ".join(f"{k} → {v}" for k, v in sorted(m["status_counts"].items())),
          "결제사 스텁은 정상으로 되돌렸습니다"]
    if m["worst"] > m["limit"] or 0 in m["product_codes"]:
        return Result(True, f"결제사가 느려지자 상품 목록이 {m['worst']:.1f}초 걸림(기준 {m['limit']}초)", ev)
    return Result(False, f"결제사가 느려도 상품 목록은 {m['worst']:.1f}초 안에 뜬다", ev)
