// 가짜 결제사. 응답이 오기까지 잠깐 기다린다(실제 결제사도 느리다).
export const payments = {
  delayMs: 50,
  async charge(userId, amount) {
    await new Promise((resolve) => setTimeout(resolve, this.delayMs));
    return { ok: true, userId, amount };
  },
};
