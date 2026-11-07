"""2-6 DNS 리바인딩: 허용한 이름이 내부를 가리킨다 · T05(로컬 운영 도구 Host 허용 목록)."""
import pytest

from replay import _logs
from replay import t05_dns_rebinding as t05


def test_rebound_host_rejected(stack):
    r = t05.run(stack)
    assert not r.vulnerable, "기대: 운영 도구는 공격자 이름이 Host에 실린 요청을 거부해야 합니다\n" + r.show()


@pytest.mark.parametrize("host", ["localhost.attacker.example:{p}", "127.0.0.1.nip.example:{p}", "attacker.example"])
def test_other_foreign_hosts_rejected(stack, host):
    r = t05.ops_get(host.format(p=t05.ops_port()), "/ops/health")
    assert r.status_code == 403, f"기대: Host {host} → 403. 실제: HTTP {r.status_code}"


def test_denied_host_is_logged(stack):
    since = _logs.now()
    t05.ops_get(f"rebind.attacker.example:{t05.ops_port()}")
    e = _logs.wait_event("mall-admin", since, "request.denied.host", "2-6", stack)
    assert e, "기대: 낯선 Host를 거부하면 mall-admin 로그에 SECURITY request.denied.host(2-6)가 남아야 합니다"
    assert (e.get("target") or "").startswith("host:rebind.attacker.example"), f"실제: {e}"


@pytest.mark.normal
@pytest.mark.parametrize("host", [None, "localhost:{p}"])
def test_ops_tool_works_from_this_pc(stack, host):
    r = t05.ops_get(host.format(p=t05.ops_port()) if host else None, "/ops/health")
    assert r.status_code == 200 and "daon-ops" in r.text, f"기대: 이 PC에서 127.0.0.1·localhost로 부르면 그대로 씁니다. 실제: HTTP {r.status_code}"
