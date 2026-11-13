// 수업 목록. 번호는 본편(「해킹 서바이벌 가이드」) 수업 번호와 같다.
export const LESSONS = {
  '2-2': { title: '주문 번호만 바꾸면 남의 주문이 보인다', files: ['app/routes/orders.js'] },
  '2-3': { title: '등록 안 한 관리자 주소가 열려 있다', files: ['app/auth.js'] },
  '2-4': { title: '내 정보 수정으로 관리자가 된다', files: ['app/routes/users.js'] },
  '2-5': { title: '이미지 주소로 내부 서버를 부른다', files: ['app/routes/images.js'] },
  '3-2': { title: '검색어 하나로 숨긴 상품까지 나온다', files: ['app/routes/products.js'] },
  '3-4': { title: '리뷰 한 줄이 남의 브라우저에서 실행된다', files: ['app/routes/products.js'] },
  '4-3': { title: '비밀번호를 끝없이 맞혀 볼 수 있다', files: ['app/auth.js'] },
  '4-8': { title: '한 번 받은 토큰이 영원히 쓰인다', files: ['app/auth.js'] },
  '6-3': { title: '비밀 값이 코드에 기본값으로 박혀 있다', files: ['app/config.js'], env: { SHOP_SECRET: '' } },
  '6-5': { title: '오류 응답에 DB 정보가 새어 나온다', files: ['app/lib/errors.js'] },
  '8-3': { title: '누가 고객 정보를 봤는지 아무도 모른다', files: ['app/lib/log.js', 'app/routes/admin.js'] },
  '8-5': { title: '로그에 비밀번호가 그대로 남는다', files: ['app/lib/log.js'] },
  '10-2': { title: '쿠폰 한 장이 여러 번 쓰인다', files: ['app/routes/coupons.js'] },
  '15-4': { title: '부품으로 방어를 연결한다', files: ['app/app.js'], bonus: true },
  '18-1': { title: '미들웨어 순서를 바로잡는다', files: ['app/app.js'], extra: true },
  '18-2': { title: '비동기 확인 실패 때 자료를 막는다', files: ['app/app.js'], extra: true },
  '18-3': { title: '쿠키와 세션을 안전하게 관리한다', files: ['app/session-demo.js'], extra: true },
  '18-4': { title: '의존성과 잠금 파일을 점검한다', files: ['extras/18-4/work/package.json', 'extras/18-4/work/package-lock.json'], extra: true },
};

export function lessonArg() {
  const raw = process.argv[2] ?? '';
  const id = raw.replace(/^0/, '').replace(/-0(\d)$/, '-$1');
  if (!LESSONS[id]) {
    console.error(`수업 번호를 확인하세요: ${raw || '(없음)'}`);
    console.error(`있는 수업: ${Object.keys(LESSONS).join(', ')}`);
    process.exit(1);
  }
  return id;
}
