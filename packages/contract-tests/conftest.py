"""계약 시험: 떠 있는 예제 앱(127.0.0.1)에 묻는다. 앱 고르기는 KIT_APPS=spring-demo,express-demo 처럼(기본: 네 개 모두)."""
from __future__ import annotations

import os
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent))

from kitcontract.client import APPS, Kit, KitNotReady  # noqa: E402


def chosen(kind: str) -> list[str]:
    want = [a.strip() for a in os.environ.get("KIT_APPS", ",".join(APPS)).split(",") if a.strip()]
    return [a for a in want if a.endswith(kind) and a in APPS]


@pytest.hookimpl(hookwrapper=True)
def pytest_runtest_call(item):
    outcome = yield
    exc = outcome.excinfo
    if exc and issubclass(exc[0], KitNotReady):
        pytest.fail(f"준비 안 됨: {exc[1]}", pytrace=False)


def kit_for(name: str) -> Kit:
    return Kit(APPS[name], name)
