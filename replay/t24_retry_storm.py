"""T24 · 결제사가 느려지자 우리 쪽 재시도가 요청을 몇 배로 늘린다(재시도 폭주, L9·A10). 수업 9-4(측정)·9-5(고치기).

가짜 결제사 스텁(9101)을 3초씩 늦추고(결제 클라이언트 시간 제한은 1초), alice 주문 9001에 결제 요청 10개를 1초 간격으로 보낸다.
60초 동안 스텁이 받은 결제 요청 수 ÷ 결제 요청 수 = 증폭 배수. 끝나면 스텁을 늘 되돌린다.
  레드: 증폭 배수가 기준(1.2배, 실측 뒤 확정)을 넘는다. 시작 상태는 클라이언트 3번 × 큐 소비자 3번 = 최대 9배
  그린: 기준 이하(재시도 한 곳 + 백오프·지터 + 재시도 예산 + 서킷 브레이커)
make score에서는 짧게(결제 요청 3개, 15초, 기준 3배) 잰다. 결제 금액·주문은 가짜이고, 스텁은 돈을 다루지 않는다.
"""
from __future__ import annotations

from replay import NotReady, Result
from replay._faults import measure_retry, retry_table

T = "T24"
TITLE = "재시도 폭주: 결제사가 느려지면 요청이 몇 배로"
LESSON = "9-4·9-5"
SOURCE = "[교육용 추가] DaonPaymentClient(간격 없이 3번) + DaonPayRequestReceiver(실패하면 바로 다시 넣기 3번)"
STACKS = ("spring",)


def run(stack: str = "spring", dynamic_run: bool = True) -> Result:
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 아직 없습니다(Express는 diffs/s9-05).")
    m = measure_retry("full" if dynamic_run else "quick", progress=print if dynamic_run else None)
    ev = ["장애 넣기: 가짜 결제사 스텁 → 3초씩 늦게(make stub-fault S=pay MODE=slow MS=3000과 같음), 끝나고 되돌림"] + retry_table(m)
    if m["accepted"] < m["orders"]:
        ev.append(f"주의: 결제 요청 {m['orders']}건 중 202(결제 대기)는 {m['accepted']}건")
    if m["ratio"] > m["ok_max"]:
        return Result(True, f"결제 요청 {m['orders']}건이 결제사 요청 {m['stub_requests']}건이 됐다({m['ratio']:.1f}배)", ev)
    return Result(False, f"결제사가 느려도 요청이 {m['ratio']:.1f}배에 머문다", ev)
