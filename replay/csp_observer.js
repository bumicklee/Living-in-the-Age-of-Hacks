// 재연·시험·캡처가 공유하는 관찰 단위: 콘솔 메시지가 아닌 DOM 위반 이벤트.
(() => {
  if (window.__daonCspInstalled) return;
  window.__daonCspInstalled = true;
  window.__daonCspEvents = [];
  document.addEventListener('securitypolicyviolation', e => {
    window.__daonCspEvents.push({
      directive: e.effectiveDirective,
      blockedURI: e.blockedURI,
      disposition: e.disposition
    });
  });
})();
