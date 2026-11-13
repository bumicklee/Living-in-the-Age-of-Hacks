// 오류 처리기. 앱의 맨 끝에 둔다.

// ✏️ 6-5: 오류 원문과 스택을 그대로 돌려준다
export function errorHandler(log) {
  return (err, req, res, next) => {
    res.status(err.status ?? 500).json({ message: err.message, stack: err.stack });
  };
}
