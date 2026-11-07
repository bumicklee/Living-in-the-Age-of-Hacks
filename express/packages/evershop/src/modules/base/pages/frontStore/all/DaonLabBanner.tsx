// 다온마켓 랩: 「교육용 랩」 띠(A10). 모든 화면 아래에 고정. 다른 동작은 없다.
import React from 'react';

export default function DaonLabBanner() {
  return (
    <div
      data-testid="lab-banner"
      style={{
        position: 'fixed',
        left: 0,
        right: 0,
        bottom: 0,
        zIndex: 9999,
        padding: '3px 12px',
        textAlign: 'center',
        background: '#fff4c2',
        color: '#5c4400',
        borderTop: '1px solid #e3c55a',
        font: '600 13px/1.5 system-ui, sans-serif',
        pointerEvents: 'none'
      }}
    >
      교육용 랩 · 127.0.0.1 · 값은 가짜
    </div>
  );
}

export const layout = {
  areaId: 'body',
  sortOrder: 100
};
