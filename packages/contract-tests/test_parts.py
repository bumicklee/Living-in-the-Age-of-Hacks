"""부품 24개 계약: 같은 함수가 Spring·Express 부품 예제 앱에 똑같이 돈다(CONTRACT.md 1절)."""
import pytest

from conftest import chosen, kit_for
from kitcontract.parts import PARTS


@pytest.fixture(params=chosen("demo"))
def kit(request):
    k = kit_for(request.param)
    k.reset()
    yield k
    k.reset()


@pytest.mark.parametrize("key", list(PARTS))
def test_part(kit, key):
    PARTS[key](kit)
