// 랩 세계: 앱 바깥에 있는 가짜 서버와 가짜 인터넷. 수강생이 고칠 곳이 아니다.
//  - 이미지 서버 images.daon.example: 정상 이미지, 이미지가 아닌 문서, 다른 곳으로 보내는 주소, 느린 응답, 큰 응답
//  - 사내 관리 서버(127.0.0.1): 밖에서 닿으면 안 되는 설정(가짜 값)
//  - 랩 주소 해석기: 랩 이름을 정해 둔 주소로 돌려준다. 이미지 서버는 문서용 공인 주소 203.0.113.10으로 보이고,
//    실제 연결만 내 PC의 가짜 서버로 이어진다.
import http from 'node:http';
import { lookup as dnsLookup } from 'node:dns/promises';

// 1×1 PNG
const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=', 'base64');
export const IMAGE_SERVER_ADDRESS = '203.0.113.10';

function listen(server, port) {
  return new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(port, '127.0.0.1', () => resolve(server.address().port));
  });
}

export async function startLabWorld({ imagesPort = 0, internalPort = 0 } = {}) {
  const internal = http.createServer((req, res) => {
    if (req.url === '/admin/secrets') {
      res.writeHead(200, { 'content-type': 'application/json; charset=utf-8' });
      return res.end(JSON.stringify({ server: '사내 관리 서버', note: '밖에서 보이면 안 되는 설정', backupKey: 'LAB-FAKE-KEY-0001' }));
    }
    res.writeHead(404).end();
  });
  const iport = await listen(internal, internalPort);

  const timers = new Set();
  const images = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://images.daon.example');
    if (url.pathname === '/cat.png') {
      res.writeHead(200, { 'content-type': 'image/png' });
      return res.end(PNG);
    }
    if (url.pathname === '/big.png') {
      res.writeHead(200, { 'content-type': 'image/png' });
      return res.end(Buffer.alloc(2 * 1024 * 1024, 7));
    }
    if (url.pathname === '/slow.png') {
      const t = setTimeout(() => { timers.delete(t); res.writeHead(200, { 'content-type': 'image/png' }); res.end(PNG); }, 5000);
      timers.add(t);
      return;
    }
    if (url.pathname === '/page.html') {
      res.writeHead(200, { 'content-type': 'text/html; charset=utf-8' });
      return res.end('<p>이미지가 아닌 문서</p>');
    }
    if (url.pathname === '/go') {
      res.writeHead(302, { location: url.searchParams.get('to') ?? '/' });
      return res.end();
    }
    res.writeHead(404).end();
  });
  const mport = await listen(images, imagesPort);

  // 랩 이름표. flip.daon.example 은 물을 때마다 답이 바뀐다(공인 주소 → 내부 주소 → …).
  let flips = 0;
  const NAMES = {
    'images.daon.example': () => [{ address: IMAGE_SERVER_ADDRESS, family: 4 }],
    'inside.daon.example': () => [{ address: '127.0.0.1', family: 4 }],
    'mixed.daon.example': () => [{ address: IMAGE_SERVER_ADDRESS, family: 4 }, { address: '127.0.0.1', family: 4 }],
    'flip.daon.example': () => [{ address: flips++ % 2 === 0 ? IMAGE_SERVER_ADDRESS : '127.0.0.1', family: 4 }],
  };

  const net = {
    // 이름을 주소 목록으로 바꾼다(dns.lookup 의 all: true 와 같은 모양)
    async lookup(hostname) {
      if (NAMES[hostname]) return NAMES[hostname]();
      if (hostname === 'localhost') return [{ address: '127.0.0.1', family: 4 }];
      const plain = hostname.replace(/^\[|\]$/g, '');
      if (/^[\d.]+$/.test(plain)) return [{ address: plain, family: 4 }];
      if (plain.includes(':')) return [{ address: plain, family: 6 }];
      return dnsLookup(hostname, { all: true });
    },

    // 정해 준 주소(address)로만 한 번 요청한다. 다른 곳으로 보내도 따라가지 않는다.
    request(url, { address, timeoutMs = 10000, maxBytes = Infinity } = {}) {
      const target = new URL(url);
      const local = address === IMAGE_SERVER_ADDRESS ? { host: '127.0.0.1', port: mport } : { host: address, port: Number(target.port || 80) };
      return new Promise((resolve, reject) => {
        const req = http.get({ ...local, path: target.pathname + target.search, headers: { host: target.host }, timeout: timeoutMs }, (res) => {
          const chunks = [];
          let size = 0;
          res.on('data', (c) => {
            size += c.length;
            if (size > maxBytes) req.destroy(new Error('응답이 너무 큽니다'));
            else chunks.push(c);
          });
          res.on('end', () => resolve({ status: res.statusCode, headers: res.headers, body: Buffer.concat(chunks) }));
          res.on('error', reject);
        });
        req.on('timeout', () => req.destroy(new Error('응답이 너무 늦습니다')));
        req.on('error', reject);
      });
    },

    // 보통 fetch 처럼: 이름을 해석해 첫 주소로 연결하고, 다른 곳으로 보내면 따라간다(제한 없음)
    async fetch(url) {
      let target = new URL(url);
      for (let hop = 0; hop < 5; hop += 1) {
        if (target.protocol !== 'http:') throw new Error(`이 랩은 http 주소만 연결합니다: ${target.protocol}`);
        const [first] = await net.lookup(target.hostname);
        const res = await net.request(target, { address: first.address });
        if (res.status >= 300 && res.status < 400 && res.headers.location) {
          target = new URL(res.headers.location, target);
          continue;
        }
        return res;
      }
      throw new Error('다른 곳으로 너무 많이 보냈습니다');
    },
  };

  return {
    net,
    imagesPort: mport,
    internalPort: iport,
    close() {
      for (const t of timers) clearTimeout(t);
      internal.closeAllConnections?.();
      images.closeAllConnections?.();
      internal.close();
      images.close();
    },
  };
}
