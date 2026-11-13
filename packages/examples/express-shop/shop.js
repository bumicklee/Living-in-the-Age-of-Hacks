// 가게 앱(회사 서비스 흉내)의 자기 로직: 로그인·주문·회원. 15-4 전후로 바뀌지 않는다.
// 자기 로그인을 쓴다(토큰 shop-<무작위>). secure-kit은 이 인증을 모른다 → 인증 미들웨어가 req.secureKitActor 를 넣어 준다.
// 모든 값은 가짜다. 전화번호는 010-0000-NNNN 모양만 쓴다.
import { pbkdf2Sync, randomBytes, timingSafeEqual } from 'node:crypto';

// shop-admin / shop-pass-1! 의 PBKDF2 해시(가짜 비밀번호)
const ADMIN_HASH = 'pbkdf2-sha256$100000$lqrojBWS0PsL6+GoGjdVpg==$tQ9X4SOxQusWKLnETk6+tuUYt0bE12gDVM67hzc39ks=';
const ACCOUNTS = [{ username: 'shop-admin', role: 'admin', passwordHash: ADMIN_HASH }];
const LOGIN_FAILED = '아이디 또는 비밀번호가 올바르지 않습니다';

const MEMBERS = Array.from({ length: 10 }, (_, i) => ({
  id: i + 1,
  name: `가게회원${String(i + 1).padStart(2, '0')}`,
  phone: `010-0000-${String(2001 + i).padStart(4, '0')}`
}));

function verify(password, stored) {
  const [, iter, salt, hash] = stored.split('$');
  const want = Buffer.from(hash, 'base64');
  const got = pbkdf2Sync(String(password ?? ''), Buffer.from(salt, 'base64'), Number(iter), want.length, 'sha256');
  return timingSafeEqual(got, want);
}

const maskPhone = (p) => p.replace(/^(\d{3})-\d{4}-(\d{4})$/, '$1-****-$2');

export function createShop() {
  const sessions = new Map(); // 토큰 → 계정

  return {
    /** 모든 요청: Bearer shop-… 이면 계정을 붙인다 */
    authenticate(req, res, next) {
      const h = req.headers.authorization ?? '';
      const account = h.startsWith('Bearer ') ? sessions.get(h.slice(7)) : undefined;
      if (account) {
        req.shopUser = account;
        // secure-kit 감사 기록·이벤트의 actor(다른 인증을 쓰는 앱의 연결 방법, CONTRACT 15번)
        req.secureKitActor = `${account.role === 'admin' ? 'admin' : 'user'}:${account.username}`;
      }
      next();
    },

    requireAdmin(req, res, next) {
      if (req.shopUser?.role === 'admin') return next();
      res.status(req.shopUser ? 403 : 401).json({ message: '권한이 없습니다' });
    },

    login(req, res) {
      const { username, password } = req.body ?? {};
      const account = ACCOUNTS.find((a) => a.username === username);
      // 없는 아이디도 같은 해시 비교를 한다
      const ok = verify(password, account?.passwordHash ?? ADMIN_HASH) && Boolean(account);
      if (!ok) return res.status(401).json({ message: LOGIN_FAILED });
      const token = `shop-${randomBytes(16).toString('hex')}`;
      sessions.set(token, { username: account.username, role: account.role });
      res.json({ token });
    },

    getOrder(req, res) {
      const { id } = req.params;
      // 흉내: 번호가 아닌 값을 쿼리에 넣다가 DB가 오류를 냈다
      if (!/^\d+$/.test(id)) throw new Error(`SQLSyntaxErrorException: Unknown column '${id.slice(0, 16)}' in 'oms_order'`);
      res.json({ id: Number(id), status: 'PAID', amount: 32000 });
    },

    getMember(req, res) {
      const m = MEMBERS.find((x) => x.id === Number(req.params.id));
      if (!m) return res.status(404).json({ message: '찾을 수 없습니다' });
      res.json({ id: m.id, name: m.name, phone: maskPhone(m.phone) });
    },

    reset(req, res) {
      sessions.clear();
      res.status(204).end();
    }
  };
}
