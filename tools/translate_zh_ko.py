#!/usr/bin/env python3
"""translate_zh_ko.py - mall 포크의 중국어 문구만 한국어로 바꾼다(영어·코드는 그대로). 2026-11-06

  python3 tools/translate_zh_ko.py tools/zh_ko.json            # spring/ 에 적용
  python3 tools/translate_zh_ko.py tools/zh_ko.json --check    # 남은 중국어만 센다

- 한 줄에서 「중국어로 시작해 중국어로 끝나는 조각」(따옴표·꺾쇠·중괄호를 넘지 않음)을 찾아 사전으로 바꾼다.
  같은 조각은 늘 같은 번역이 된다.
- 번역하지 않는 곳: 원본 설계 문서(document/pdm·pos·axure·mind), 함께 실린 라이브러리(mall-admin-web/public/tinymce*).
- 어순이 달라 조각 번역으로 안 되는 코드(프로모션 문구 조립)는 PRE에서 줄째 먼저 바꾼다. 동작은 그대로, 문구만 바뀐다.
"""
import json
import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "spring"
CJK = r"一-鿿　-〿＀-￯"
SPAN = re.compile(r"[" + CJK + r"](?:[^\n\"'<>`{}]*[" + CJK + r"])?")
HAN = re.compile(r"[一-鿿]")
SKIP = ("document/pdm", "document/pos", "document/axure", "document/mind", "public/tinymce", "/.git", "node_modules", "/target")
EXTS = {".java", ".sql", ".yml", ".yaml", ".xml", ".json", ".md", ".vue", ".ts", ".js", ".scss", ".env",
        ".production", ".development", ".properties", ".sh", ".html", ".conf"}

PROMO = "mall-portal/src/main/java/com/macro/mall/portal/service/impl/OmsPromotionServiceImpl.java"
PRE = {PROMO: [
    ('sb.append("满减优惠：");\n        sb.append("满");\n        sb.append(fullReduction.getFullPrice());\n'
     '        sb.append("元，");\n        sb.append("减");\n        sb.append(fullReduction.getReducePrice());\n        sb.append("元");',
     'sb.append("금액 할인: ");\n        sb.append(fullReduction.getFullPrice());\n'
     '        sb.append("원 이상 구매 시 ");\n        sb.append(fullReduction.getReducePrice());\n        sb.append("원 할인");'),
    ('sb.append("打折优惠：");\n        sb.append("满");\n        sb.append(ladder.getCount());\n        sb.append("件，");\n'
     '        sb.append("打");\n        sb.append(ladder.getDiscount().multiply(new BigDecimal(10)));\n        sb.append("折");',
     'sb.append("수량 할인: ");\n        sb.append(ladder.getCount());\n        sb.append("개 이상 구매 시 정가의 ");\n'
     '        sb.append(ladder.getDiscount().multiply(new BigDecimal(10)));\n        sb.append("할 가격");'),
]}
# 번역 뒤 한국어가 중국어보다 길어 들어가지 않는 칸: 길이만 늘린다(UPSTREAM.md에 기록)
POST = {"document/sql/mall.sql": [
    ("`promotion_info` varchar(100)", "`promotion_info` varchar(500)"),
],
    # 라이브러리 화면 언어: Element Plus는 한국어, TinyMCE는 언어 설정을 빼서 기본 영어로(한국어 언어 파일은 따로 받아야 함)
    "mall-admin-web/src/App.vue": [
        ("import zhCn from 'element-plus/es/locale/lang/zh-cn'", "import ko from 'element-plus/es/locale/lang/ko'"),
        (':locale="zhCn"', ':locale="ko"'),
    ],
    "mall-admin-web/src/components/Tinymce/index.vue": [
        ("  language: 'zh_CN',\n  language_url: './tinymce6.8.6/langs/zh_CN.js',\n", ""),
    ],
}


def promo_text(s):
    """시드 데이터의 프로모션 문구를 코드가 만드는 문구와 같은 꼴로."""
    s = re.sub(r"满减优惠：满([\d.]+)元，减([\d.]+)元", r"금액 할인: \1원 이상 구매 시 \2원 할인", s)
    s = re.sub(r"打折优惠：满(\d+)件，打([\d.]+)折", r"수량 할인: \1개 이상 구매 시 정가의 \2할 가격", s)
    return s


def translate(s, d):
    p = promo_text(s)
    if p == s:
        return d.get(s, s)
    # 프로모션 문구가 섞인 시드 값: 쉼표·세미콜론으로 나눠 나머지 조각만 사전으로
    out = []
    for part in re.split(r"([,;，；])", p):
        part = {"，": ",", "；": ";"}.get(part, part)
        out.append(d.get(part, part) if HAN.search(part) else part)
    return "".join(out)


def files():
    for root, ds, fs in os.walk(ROOT):
        r = root.replace(os.sep, "/")
        if any(x in r for x in SKIP):
            continue
        for f in fs:
            if os.path.splitext(f)[1] in EXTS or f == "Dockerfile":
                yield Path(root) / f


def main():
    d = json.load(open(sys.argv[1], encoding="utf-8"))
    check = "--check" in sys.argv
    changed, left = 0, {}
    for p in files():
        try:
            t = p.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        if not check:
            rel = p.relative_to(ROOT).as_posix()
            for a, b in PRE.get(rel, []):
                if a not in t:
                    sys.exit(f"PRE 대상 문장을 못 찾음: {rel}")
                t = t.replace(a, b)
            t2 = SPAN.sub(lambda m: translate(m.group(0), d), t)
            for a, b in POST.get(rel, []):
                if a not in t2:
                    sys.exit(f"POST 대상 문장을 못 찾음: {rel}")
                t2 = t2.replace(a, b)
            if t2 != p.read_text(encoding="utf-8"):
                p.write_text(t2, encoding="utf-8")
                changed += 1
            t = t2
        n = sum(1 for line in t.splitlines() if HAN.search(line))
        if n:
            left[p.relative_to(ROOT).as_posix()] = n
    print(f"바꾼 파일 {changed}개, 중국어가 남은 파일 {len(left)}개 ({sum(left.values())}줄)")
    for k, v in sorted(left.items(), key=lambda x: -x[1])[:20]:
        print(f"  {v:5d}  {k}")


if __name__ == "__main__":
    main()
