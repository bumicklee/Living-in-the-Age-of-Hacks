"""T31 · 주문 메일 서버가 줄 끝 LF 하나뿐인 메일 끝 표시를 받아 준다(SMTP 밀반입 조건, P4). 수업 11-5.

[회사 설정] 랩에는 메일 서버가 없다(Mailpit은 받는 쪽 도구). 저장소의 설정 사본 infra/mail/postfix-main.cf와
영역 파일의 DMARC 정책을 읽는 **설정 점검**만 한다. 메일을 보내지 않는다.
  레드: 패치 전 버전·smtpd_forbid_bare_newline 꺼짐·reject_unauth_pipelining 없음·DMARC p=none 중 하나라도
  그린: 모두 통과
"""
from __future__ import annotations

from daon import boundary
from replay import Result

T = "T31"
TITLE = "메일 서버가 줄 끝 LF 하나를 메일 끝으로 받아 줌(설정 점검)"
LESSON = "11-5"
SOURCE = "[회사 설정] infra/mail/postfix-main.cf(사본): Postfix 3.8.3, smtpd_forbid_bare_newline 없음, DMARC p=none"
STACKS = ("spring", "express")


def run(stack: str = "spring") -> Result:
    rows = boundary.mail_rows()
    ev = [f"{'고칠 것' if ok is False else ('확인' if ok else '참고'):<6} {name}: {val}" for name, val, ok, _ in rows]
    bad = boundary.mail_failed(rows)
    if bad:
        return Result(True, f"메일 설정 점검 고칠 것 {len(bad)}개(메일은 보내지 않음)", ev)
    return Result(False, "메일 설정 점검 모두 통과", ev)
