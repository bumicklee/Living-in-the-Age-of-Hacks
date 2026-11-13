"""15-4 가게 예제 계약 셋(CONTRACT.md 2절). 시작 지점 레드 → 부품 셋을 붙인 완료 지점 그린."""
import pytest

from conftest import chosen, kit_for
from kitcontract.shop import CHECKS


@pytest.fixture(params=chosen("shop"))
def shop(request):
    return kit_for(request.param)


@pytest.mark.parametrize("key", list(CHECKS))
def test_shop(shop, key):
    CHECKS[key](shop)
