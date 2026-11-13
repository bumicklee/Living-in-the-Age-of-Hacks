"""랩 유출 비밀번호 목록(seed/password/breached-top.txt)을 만든다. 4-2·4-3.

실제 유출 데이터가 아니다. 사람들이 흔히 고르는 모양(단어 + 숫자·기호, 키보드 줄, 같은 숫자 반복)을
규칙으로 만든 **가짜 목록**이다. 실제 유출 목록은 라이선스와 배포 문제가 있어 랩에 넣지 않는다.
운영에서는 HIBP 같은 k-익명성 조회나 회사가 받은 목록을 쓴다(수업에서 말한다).

    python tools/gen_password_list.py        # 몇 번을 돌려도 같은 파일

- 한 줄에 하나, 소문자. 앱(PasswordPolicy)은 소문자로 바꾼 입력의 SHA-1을 이 목록의 SHA-1 집합과 비교한다.
- 맨 앞 줄들은 재연·시험이 쓰는 값이라 순서를 바꾸지 않는다(1번 값 = 재연 가입 비밀번호).
- 랩 가짜 계정 비밀번호(daon-pass-1!)는 넣지 않는다.
"""
from __future__ import annotations

from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "seed" / "password" / "breached-top.txt"

# 재연·시험이 쓰는 앞쪽 값(8자 이상: 길이 규칙이 아니라 유출 목록 때문에 거부되는 것을 보이려고)
HEAD = ["password123", "qwer1234!", "1q2w3e4r!", "iloveyou1", "sunshine1", "dragon123", "letmein!", "welcome1",
        "abcd1234", "qwerty123"]

WORDS = ["password", "passw0rd", "qwerty", "qwer", "asdf", "zxcv", "iloveyou", "sunshine", "princess", "dragon",
         "monkey", "letmein", "welcome", "football", "baseball", "soccer", "master", "shadow", "superman", "batman",
         "michael", "jessica", "charlie", "freedom", "whatever", "trustno1", "hello", "secret", "summer", "winter",
         "spring", "autumn", "flower", "cookie", "chocolate", "coffee", "banana", "orange", "apple", "computer",
         "internet", "samsung", "galaxy", "iphone", "android", "google", "naver", "kakao", "daum", "korea",
         "seoul", "busan", "sarang", "saranghae", "gkstn", "dkssud", "tkfkd", "admin", "administrator", "root",
         "user", "guest", "test", "login", "shop", "market", "mall", "store", "money", "lucky",
         "happy", "smile", "angel", "tiger", "lion", "puppy", "kitty", "love", "lovely", "pretty"]

SUFFIXES = ["", "1", "12", "123", "1234", "12345", "!", "1!", "12!", "123!", "1234!", "@", "@1", "@123", "#1",
            "00", "01", "07", "11", "69", "77", "88", "99", "2020", "2021", "2022", "2023", "2024", "2025", "2026",
            "!!", "?", "*", "1q", "0", "2", "3", "7", "9", "10", "13", "21", "22", "23", "24", "25", "26", "100",
            "777", "1004", "486", "8282", "0000", "1111", "2580", "!@#", "!@#$", "$", "^^", "~", "_1", ".1",
            "123456", "321", "987", "4321", "abc", "a1", "q1", "z1", "x", "xx", "kr", "ko", "go", "s", "z",
            "1980", "1985", "1988", "1990", "1992", "1995", "1998", "2000", "2002", "2005", "2010", "2015",
            "2018", "2019", "11!", "22!", "33", "44", "55", "66", "123qwe", "qwe", "asd", "zxc", "!q", "1@",
            "12#", "pw", "pass", "0101", "1225", "0815", "1212", "1313", "2468", "1357", "9999", "8888", "5555",
            "4444", "3333", "2222", "6666", "7777", "!0", "!2", "!3", "!7", "!9", "#", "#123", "@@", "**"]

KEYBOARD = ["qwerty", "qwertyuiop", "asdfgh", "asdfghjkl", "zxcvbn", "zxcvbnm", "1q2w3e", "1q2w3e4r", "1q2w3e4r5t",
            "1qaz2wsx", "1qaz2wsx3edc", "qazwsx", "qazwsxedc", "q1w2e3r4", "a1s2d3f4", "z1x2c3v4", "qwer1234",
            "asdf1234", "zxcv1234", "!qaz2wsx", "1qazxsw2", "qwe123", "asd123", "zxc123", "qweasd", "qweasdzxc"]


def build() -> list[str]:
    seen: set[str] = set()
    out: list[str] = []

    def add(p: str) -> None:
        p = p.lower()
        if p and p not in seen and p != "daon-pass-1!":
            seen.add(p)
            out.append(p)

    for p in HEAD:
        add(p)
    for d in "0123456789":
        for n in range(6, 13):
            add(d * n)
    for n in range(6, 13):
        add("1234567890"[:n] if n <= 10 else "123456789012"[:n])
        add("9876543210"[:n] if n <= 10 else "987654321098"[:n])
    for k in KEYBOARD:
        for s in ["", "!", "1", "12", "123", "!@", "!@#"]:
            add(k + s)
    for w in WORDS:
        for s in SUFFIXES:
            add(w + s)
            add(w.capitalize() + s)
    for w in WORDS:                                           # 단어 두 개(흔한 조합만)
        for w2 in ["love", "123", "kim", "lee", "park", "baby", "boy", "girl", "man", "king", "queen", "star"]:
            for s in ["", "1", "!", "12", "123", "2025", "2026"]:
                add(w + w2 + s)
    return out


def main() -> None:
    rows = build()
    OUT.parent.mkdir(parents=True, exist_ok=True)
    head = ["# 랩 유출 비밀번호 목록(가짜). tools/gen_password_list.py가 만든다. 손으로 고치지 않는다.",
            "# 실제 유출 데이터가 아니다. 흔한 비밀번호 모양을 규칙으로 만든 교육용 목록이다(4-2·4-3).",
            f"# {len(rows)}줄 · 소문자 · 앞 10줄은 재연·시험이 쓴다."]
    OUT.write_text("\n".join(head + rows) + "\n", "utf-8")
    print(f"{OUT.relative_to(OUT.parent.parent.parent)}: {len(rows)}줄")


if __name__ == "__main__":
    main()
