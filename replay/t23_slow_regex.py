"""T23 · 쿠폰 코드 확인의 정규식이 입력 길이에 따라 급격히 느려진다(느린 정규식, P8·A10). 수업 9-1(측정)·9-3(고치기).

alice로 GET /daon/coupon/check?code=AAAA…! 를 길이 14자부터 한 글자씩 늘려 보낸다(한 번에 하나, 요청마다 2초 제한).
1초를 넘는 길이가 나오면 그 뒤는 보내지 않는다. 최대 28자, 15개 이하(replay/_faults.py에 박아 둠).
  레드: 0.5초 이상 걸린(또는 2초 제한에 걸린) 길이가 있다
  그린: 모든 길이가 0.5초 안(9-3: 20자 넘으면 바로 400, 20자 이하는 선형 시간 엔진 RE2/J)
측정 뒤 서버 CPU가 내려갈 때까지 기다리려면 make measure-cooldown.
"""
from __future__ import annotations

from replay import NotReady, Result
from replay._faults import measure_regex, regex_slow, regex_table

T = "T23"
TITLE = "느린 정규식: 입력 길이가 늘면 처리 시간이 두 배씩"
LESSON = "9-1·9-3"
SOURCE = "[교육용 추가] DaonCouponController의 겹치는 1~3글자 갈래와 중첩 반복. 시작 코드에는 랩 보호용 28자 상한만 있다. 페이지 크기·수량 상한 없음(F41·F42)은 make check L=9-3"
STACKS = ("spring",)


def run(stack: str = "spring") -> Result:
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 아직 없습니다(Express는 diffs/s9-03).")
    rows = measure_regex()
    ev = regex_table(rows)
    if regex_slow(rows):
        last = rows[-1]
        return Result(True, f"{last['len']}자 입력에서 " + ("2초 제한에 걸렸다" if last["sec"] is None else f"{last['sec']:.2f}초가 걸렸다")
                      + " (몇 글자 늘 때마다 시간이 두 배씩)", ev)
    return Result(False, "모든 길이가 0.5초 안에 판정됐다(길이 상한 + 선형 시간 엔진)", ev)
