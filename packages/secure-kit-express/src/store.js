// 저장소 인터페이스와 메모리 구현(기본).
// 서버가 여러 대이면 같은 모양으로 Redis 구현을 넣는다(README 「Redis로 바꾸기」). 모든 메서드는 Promise를 돌려준다.
//
//   get(key)                      → 값 | undefined
//   set(key, value, ttlMs)        → void
//   setIfAbsent(key, value, ttlMs)→ true(새로 넣음) | false(이미 있음)   ← Redis: SET key v NX PX ttl
//   incr(key, ttlMs)              → { count, ttlMs(남은 시간) }          ← Redis: INCR + PEXPIRE(처음 한 번) + PTTL
//   del(key)                      → void
//   clear()                       → void(랩 /_kit/reset 전용)

export class MemoryStore {
  constructor() {
    this.map = new Map();
  }

  #live(key) {
    const e = this.map.get(key);
    if (!e) return undefined;
    if (e.exp && e.exp <= Date.now()) {
      this.map.delete(key);
      return undefined;
    }
    return e;
  }

  async get(key) {
    const e = this.#live(key);
    // 꺼낸 값을 고쳐도 저장된 값이 바뀌지 않게 복사해서 준다(Redis와 같은 동작)
    return e ? structuredClone(e.value) : undefined;
  }

  async set(key, value, ttlMs) {
    this.map.set(key, { value: structuredClone(value), exp: ttlMs ? Date.now() + ttlMs : 0 });
  }

  async setIfAbsent(key, value, ttlMs) {
    if (this.#live(key)) return false;
    await this.set(key, value, ttlMs);
    return true;
  }

  async incr(key, ttlMs) {
    const e = this.#live(key);
    if (!e) {
      this.map.set(key, { value: 1, exp: Date.now() + ttlMs });
      return { count: 1, ttlMs };
    }
    e.value += 1;
    return { count: e.value, ttlMs: Math.max(0, e.exp - Date.now()) };
  }

  async del(key) {
    this.map.delete(key);
  }

  async clear() {
    this.map.clear();
  }
}

/** 쓰는 쪽에서 넘긴 저장소가 인터페이스를 다 갖췄는지 기동 때 확인한다 */
export function assertStore(store) {
  for (const m of ['get', 'set', 'setIfAbsent', 'incr', 'del', 'clear']) {
    if (typeof store?.[m] !== 'function') throw new Error(`secure-kit: 저장소에 ${m}() 가 없습니다`);
  }
  return store;
}
