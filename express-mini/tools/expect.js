// 시험 문장을 짧게 쓰는 도우미. 실패하면 한국어 이유를 남긴다.
export function expectStatus(res, want, what) {
  if (res.status !== want) throw new Error(`${what}: 응답 ${want}이어야 하는데 ${res.status}`);
}
export function expectTrue(value, message) {
  if (!value) throw new Error(message);
}
