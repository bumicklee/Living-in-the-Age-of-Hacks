// @secure-kit/express 타입(손으로 쓴 것). 소스는 빌드 없이 도는 순수 JS(ES 모듈)다.
// express 타입에 묶이지 않도록 요청·응답은 Node http 타입을 넓힌 최소 모양으로 둔다.
import type { IncomingMessage, ServerResponse } from 'node:http';

export interface KitRequest extends IncomingMessage {
  body?: any;
  rawBody?: Buffer;
  params?: Record<string, string>;
  path?: string;
  originalUrl?: string;
  secureKitTraceId?: string;
  /** 다른 인증을 쓰는 앱이 직접 넣는 행위자: 'admin:아이디' | 'user:아이디' | 'partner:앱' */
  secureKitActor?: string;
  secureKitUser?: { sub: string; roles: string[]; claims?: Record<string, unknown> };
  secureKitPartner?: { app: string; scopes: string[]; claims?: Record<string, unknown> };
  upload?: { stored: string; type: string; ext: string; bytes: number; buffer: Buffer };
}
export type KitResponse = ServerResponse & { json?: (body: unknown) => unknown };
export type Next = (err?: unknown) => void;
export type Middleware = (req: KitRequest, res: KitResponse, next: Next) => void | Promise<void>;
export type ErrorMiddleware = (err: unknown, req: KitRequest, res: KitResponse, next: Next) => void;

/** application.yml 의 secure-kit.* 와 같은 kebab-case 키 */
export type SecureKitConfig = Record<string, any>;

export interface SecurityEvent {
  seq: number; event: string; part: string; level: 'WARN' | 'INFO';
  actor: string; target: string; reason: string; ts: string; trace_id: string;
}
export interface AuditRecord {
  ts: string; trace_id: string; actor: string; action: string;
  target: string; count: number; reason: string; result: 'ok' | 'denied' | 'error';
}

/** 저장소 인터페이스. 기본은 MemoryStore, 서버가 여러 대면 같은 모양의 Redis 구현 */
export interface KitStore {
  get(key: string): Promise<any>;
  set(key: string, value: any, ttlMs?: number): Promise<void>;
  setIfAbsent(key: string, value: any, ttlMs?: number): Promise<boolean>;
  incr(key: string, ttlMs: number): Promise<{ count: number; ttlMs: number }>;
  del(key: string): Promise<void>;
  clear(): Promise<void>;
}
export class MemoryStore implements KitStore {
  get(key: string): Promise<any>;
  set(key: string, value: any, ttlMs?: number): Promise<void>;
  setIfAbsent(key: string, value: any, ttlMs?: number): Promise<boolean>;
  incr(key: string, ttlMs: number): Promise<{ count: number; ttlMs: number }>;
  del(key: string): Promise<void>;
  clear(): Promise<void>;
}

export class KitHttpError extends Error {
  constructor(status: number, message: string, extra?: { body?: Record<string, unknown>; headers?: Record<string, string> });
  status: number;
  body?: Record<string, unknown>;
  headers?: Record<string, string>;
}

export interface KitLogger {
  info(...args: unknown[]): void;
  warn(...args: unknown[]): void;
  error(...args: unknown[]): void;
  raw(level: string, text: string): void;
  lines(): string[];
}

export interface TokenService {
  issue(user: { sub: string; roles?: string[]; authTime?: number }): { token: string; expiresIn: number };
  issuePartner(app: string, scopes?: string[]): { token: string; expiresIn: number };
  verify(token: string, opts?: { aud?: string }): Promise<Record<string, any>>;
  revoke(claims: { jti: string; exp: number }): Promise<void>;
  authenticate(opts?: { required?: boolean; aud?: string }): Middleware;
  audience: string;
  partnerAudience: string;
  ttlSeconds: number;
}

export interface SecureKit {
  config: SecureKitConfig;
  store: KitStore;
  events: { emit(req: KitRequest | null, e: Partial<SecurityEvent> & { event: string; part: string }): SecurityEvent; after(seq?: number): { seq: number; events: SecurityEvent[] }; lastSeq(): number };
  auditTrail: { write(r: AuditRecord): void; recent(): AuditRecord[] };
  approvals: { approve(id: string, approver: string, req?: KitRequest): Promise<{ status: 'APPROVED' }> };
  outbox: { to: string; subject: string; body: string }[];
  logger(): KitLogger;

  safeFetch(): (url: string, init?: { method?: string; headers?: Record<string, string>; req?: KitRequest }) => Promise<{ status: number; headers: Record<string, unknown>; body: Buffer }>;
  hostAllowlist(): Middleware;
  safeRedirect(): { check(v: string): string | null; resolve(req: KitRequest, v: unknown): string; redirect(req: KitRequest, res: KitResponse, v: unknown, status?: number): void };
  upload(): Middleware;
  headers(): Middleware;
  rateLimit(name: string): Middleware;
  passwordPolicy(opts?: { breachedCheck?: (pw: string) => Promise<boolean> }): { check(password: string, req?: KitRequest): Promise<{ ok: boolean; reason?: 'too_short' | 'too_long' | 'breached' }> };
  constantTimeLogin<U extends { passwordHash: string }>(opts: { findUser(username: string): Promise<U | null>; iterations?: number }): {
    login(username: unknown, password: unknown): Promise<U | null>;
    hashPassword(pw: string): Promise<string>;
    verifyPassword(pw: string, stored: string): Promise<boolean>;
  };
  resetLink(): { issue(req: KitRequest, user: { id: string | number; email: string } | null): Promise<string | null>; consume(req: KitRequest, token: unknown): Promise<string | number> };
  requireStepUp(opts: { verify(req: KitRequest, password: string): Promise<boolean>; action?: string }): Middleware;
  token(): TokenService;
  fieldCrypto(): {
    encrypt(plain: string): string; decrypt(value: string, req?: KitRequest): string; blindIndex(plain: string): string;
    seal<T extends object>(record: T, fields: string[]): T & Record<string, string>;
    open<T extends object>(record: T, fields: string[], opts?: { mask?: boolean; req?: KitRequest }): T; mask(phone: string): string;
  };
  startupCheck(): { check(props: Record<string, unknown>, prefix?: string): { ok: boolean; denied: string[] }; assert(props: Record<string, unknown>, prefix?: string): { ok: true; denied: [] } };
  errorResponse(): ErrorMiddleware;
  notFound(): Middleware;
  audited(action: string, opts?: { target?: (req: KitRequest) => string; count?: (req: KitRequest, body: unknown) => number; reasonHeader?: string }): Middleware;
  bodyLimit(): Middleware;
  safeRegex(pattern: string | RegExp, opts?: { maxLength?: number; name?: string; flags?: string }): { test(req: KitRequest, value: unknown): boolean; source: string; maxLength: number };
  failClosed(check: (req: KitRequest) => Promise<boolean> | boolean, opts?: { timeoutMs?: number; dependency?: string }): Middleware;
  resilientFetch(name?: string, opts?: { fetchImpl?: typeof fetch }): (url: string, init?: RequestInit, req?: KitRequest) => Promise<Response>;
  idempotent(opts?: { header?: string; ttl?: string }): Middleware;
  webhook(): Middleware;
  budget(name: string, opts?: { key?: (req: KitRequest) => unknown; targetType?: string }): Middleware;
  noStoreAuthenticated(): Middleware;
  partnerScope(scope: string, opts?: { bind?: (req: KitRequest, partner: { app: string; scopes: string[] }) => boolean }): Middleware;
  requiresApproval(policy: string, opts?: { amount?: (req: KitRequest) => number; subject?: (req: KitRequest) => unknown; header?: string }): Middleware;

  /** /_kit/* (secure-kit.lab.expose: true 일 때만) */
  lab(opts?: { routes?: Record<string, (req: KitRequest, res: KitResponse, ctx: { params: Record<string, string>; query: URLSearchParams; body: any }) => unknown> }): Middleware;
  reset(): Promise<void>;
  onReset(fn: () => unknown): void;
}

export function secureKit(config?: SecureKitConfig, options?: {
  store?: KitStore;
  mailer?: (mail: { to: string; subject: string; body: string }) => Promise<void>;
  auditSink?: (record: AuditRecord) => unknown;
  logWrite?: (line: string) => void;
}): SecureKit;
export default secureKit;

export function traceId(req: KitRequest, res?: KitResponse): string;
export function actorOf(req: KitRequest): string;
/** 계약 모양 { code, message, traceId, ...extra } 로 바로 보낸다 */
export function sendError(req: KitRequest, res: KitResponse, status: number, message: string, extra?: Record<string, unknown>, headers?: Record<string, string>): void;
export function hashPassword(password: string, iterations?: number): Promise<string>;
export function verifyPassword(password: string, stored: string): Promise<boolean>;
export function maskPhone(text: string): string;
export function maskText(text: string): string;
export function isInternalAddress(ip: string): boolean;
