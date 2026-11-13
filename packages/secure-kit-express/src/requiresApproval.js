// 부품 24 approval · kit.requiresApproval('refund.high-value')  (수업 13-3·13-4)
// 고액 환불처럼 한 사람이 끝내면 안 되는 작업에 한 줄로 끼운다(②). 「두 번째 사람」이 승인해야 실행된다.
// 설정: approval: { policies: { 'refund-high-value': { threshold: 300000, ttl: '24h' } } }   ← 정책 이름의 점은 '-'로
// 흐름
//   기준 미만                          → 그대로 실행
//   기준 이상 + X-Approval-Id 없음      → 202 { status: 'PENDING_APPROVAL', approvalId } (실행 안 함)
//   승인 번호 있음: 없는 번호 404 · 승인 전 403 · 요청 내용(지문)이 다름 403 · 이미 씀 409 · 승인됨 → 한 번만 실행
// 승인: kit.approvals.approve(id, approver)  요청자 본인 → 403 approval.denied.self_approval
import { createHash, randomBytes } from 'node:crypto';
import { actorOf, KitHttpError, sendError, sendJson } from './http.js';
import { parseDuration, stableStringify } from './config.js';

const PART = 'approval';

/** 승인 기록 저장소(정책이 여럿이어도 하나). 상태: PENDING → APPROVED → USED */
export function createApprovals({ events, store }) {
  const key = (id) => `approval:${id}`;

  async function create(rec, ttlMs) {
    const id = `ap-${randomBytes(6).toString('hex')}`;
    await store.set(key(id), { ...rec, id, status: 'PENDING' }, ttlMs);
    return id;
  }

  /** 승인자는 요청자와 달라야 한다. 승인 서비스·관리 화면이 이 함수를 부른다 */
  async function approve(id, approver, req) {
    const rec = await store.get(key(id));
    if (!rec) throw new KitHttpError(404, '찾을 수 없습니다');
    if (!approver || approver === rec.requester) {
      events.emit(req, { event: 'approval.denied.self_approval', part: PART, actor: `admin:${approver || 'unknown'}`, target: `approval:${id}`, reason: '요청자 본인 승인' });
      throw new KitHttpError(403, '요청한 사람은 승인할 수 없습니다');
    }
    if (rec.status !== 'PENDING') throw new KitHttpError(409, '이미 처리된 승인입니다');
    await store.set(key(id), { ...rec, status: 'APPROVED', approver }, rec.ttlMs);
    events.emit(req, { event: 'approval.granted', part: PART, level: 'INFO', actor: `admin:${approver}`, target: `approval:${id}`, reason: '두 번째 사람 승인' });
    return { status: 'APPROVED' };
  }

  return { create, approve, get: (id) => store.get(key(id)), put: (rec) => store.set(key(rec.id), rec, rec.ttlMs) };
}

/**
 * @param opts.amount   (req) => 금액   기본 req.body.amount
 * @param opts.subject  (req) => 승인 대상 내용(지문에 쓴다)  기본 req.body 전체
 */
export function createRequiresApproval({ events, approvals, policy, cfg = {}, opts = {}, enabled = true }) {
  const pcfg = cfg.policies?.[policy.replace(/\./g, '-')] ?? cfg.policies?.[policy];
  if (!pcfg || typeof pcfg.threshold !== 'number') throw new Error(`secure-kit: approval.policies.${policy.replace(/\./g, '-')}.threshold 설정이 필요합니다`);
  const ttlMs = parseDuration(pcfg.ttl, 24 * 3_600_000);
  const amountOf = opts.amount ?? ((req) => Number(req.body?.amount));
  const subjectOf = opts.subject ?? ((req) => req.body ?? {});
  const header = (opts.header ?? 'X-Approval-Id').toLowerCase();

  return async function requiresApproval(req, res, next) {
    if (!enabled) return next();
    try {
      const amount = amountOf(req);
      if (!Number.isFinite(amount)) return sendError(req, res, 400, '금액이 필요합니다');
      if (amount < pcfg.threshold) return next();

      const requester = req.secureKitUser?.sub ?? actorOf(req);
      const fp = createHash('sha256').update(`${policy}|${stableStringify(subjectOf(req))}`).digest('hex');
      const id = req.headers[header];
      if (typeof id !== 'string' || !id) {
        const approvalId = await approvals.create({ policy, requester, fp, ttlMs }, ttlMs);
        events.emit(req, { event: 'approval.pending.second_approver', part: PART, level: 'INFO', target: `approval:${approvalId}`, reason: `${policy} 기준 이상` });
        return sendJson(res, 202, { status: 'PENDING_APPROVAL', approvalId });
      }
      const rec = await approvals.get(id);
      if (!rec || rec.policy !== policy) return sendError(req, res, 404, '찾을 수 없습니다');
      if (rec.status === 'USED') {
        events.emit(req, { event: 'approval.denied.reused', part: PART, target: `approval:${id}`, reason: '이미 쓴 승인' });
        return sendError(req, res, 409, '이미 처리된 요청입니다');
      }
      if (rec.fp !== fp || rec.requester !== requester) {
        events.emit(req, { event: 'approval.denied.mismatch', part: PART, target: `approval:${id}`, reason: '승인 때와 다른 요청' });
        return sendError(req, res, 403, '승인 내용과 다릅니다');
      }
      if (rec.status !== 'APPROVED') {
        events.emit(req, { event: 'approval.denied.not_approved', part: PART, level: 'INFO', target: `approval:${id}`, reason: '승인 전' });
        return sendError(req, res, 403, '아직 승인되지 않았습니다');
      }
      await approvals.put({ ...rec, status: 'USED' }); // 실행 전에 먼저 「썼음」으로(같은 승인으로 두 번 실행 막기)
      next();
    } catch (err) {
      next(err); // 저장소 오류 → 오류 응답(통과시키지 않는다)
    }
  };
}
