"""시험은 떠 있는 랩(127.0.0.1)에 HTTP로 묻는다. 재연(replay/)과 같은 코드를 쓴다."""
from __future__ import annotations

import os
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from replay import NotReady  # noqa: E402


@pytest.fixture
def stack() -> str:
    return os.environ.get("DAON_STACK", "spring")


@pytest.hookimpl(hookwrapper=True)
def pytest_runtest_call(item):
    outcome = yield
    exc = outcome.excinfo
    if exc and issubclass(exc[0], NotReady):
        # 랩이 안 떠 있으면 실패로 센다(verify-course가 "이미 그린"으로 잘못 세지 않게). 이유는 한국어로.
        pytest.fail(f"준비 안 됨: {exc[1]}", pytrace=False)
