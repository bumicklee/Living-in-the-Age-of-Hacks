"""교육용 Redis 시험: 지정한 두 키만 보존·수정하고 예외에도 남은 TTL로 복구한다."""
from contextlib import contextmanager
import json
import time
from daon import stack as lab


def redis(*args, stdin=None):
    r = lab.redis_cli(*args, stdin=stdin)
    if r.returncode or r.stdout.startswith(('ERR ', 'NOAUTH ', 'WRONGPASS ')):
        raise RuntimeError('Redis 시험 명령 실패: ' + r.stderr[:120])
    return r.stdout.removesuffix('\n')


def get(key):
    return json.loads(redis('--json', 'GET', key))


def set_value(key, value):
    assert redis('-x', 'SET', key, stdin=value) == 'OK'


@contextmanager
def preserved(*keys):
    """GET와 PTTL을 한 Redis 명령에서 읽고 실제 경과 시간을 보존한다."""
    started = time.monotonic()
    snap = json.loads(redis('--json', 'EVAL',
        "local r={} for i,k in ipairs(KEYS) do r[i]={redis.call('GET',k) or false,redis.call('PTTL',k)} end return r",
        str(len(keys)), *keys))
    try:
        yield snap
    finally:
        elapsed = int((time.monotonic() - started) * 1000)
        args = []
        for value, ttl in snap:
            remaining = ttl - elapsed if ttl >= 0 else ttl
            args.extend([value or '', str(remaining), '1' if value is not None and (ttl == -1 or remaining > 0) else '0'])
        redis('EVAL', "for i,k in ipairs(KEYS) do local a=(i-1)*3 if ARGV[a+3]=='0' then redis.call('DEL',k) else redis.call('SET',k,ARGV[a+1]) if tonumber(ARGV[a+2])>0 then redis.call('PEXPIRE',k,ARGV[a+2]) end end end return 1",
              str(len(keys)), *keys, *args)


def _typed(x):
    """Jackson 기본 형 정보 ["클래스 이름", 값] 이면 True. 7-7 전(6-4 완료 지점 등)의 캐시 저장 형식이다."""
    return isinstance(x, list) and len(x) == 2 and isinstance(x[0], str)


def fields(x):
    """캐시에 저장된 한 항목의 실제 값. 7-7 전 형식 ["com.macro.mall.model.UmsAdmin", {...}]이면 {...}만."""
    return x[1] if _typed(x) else x


def admin_id(username):
    raw = get('mall:ums:admin:' + username)
    assert raw is not None, '로그인한 직원의 캐시가 없음'
    data = json.loads(raw)
    if isinstance(data, str):
        data = json.loads(data)
    data = fields(data)
    assert isinstance(data, dict) and data['username'] == username, f'직원 캐시 형식을 읽지 못함: {type(data).__name__}'
    return int(data['id'])


def resource_rows(raw):
    """권한 목록의 항목을 저장된 모양 그대로 돌려준다(다시 쓸 때 형식 유지). 칸 값은 fields(항목)으로 읽는다."""
    data = json.loads(raw)
    if isinstance(data, str):
        return json.loads(data)
    return data[1] if _typed(data) else data


def replace_rows(raw, rows):
    data = json.loads(raw)
    if isinstance(data, str):
        data = json.dumps(rows, ensure_ascii=False)
    elif isinstance(data, list) and len(data)==2 and isinstance(data[0], str):
        data[1] = rows
    else:
        data = rows
    return json.dumps(data, ensure_ascii=False)
