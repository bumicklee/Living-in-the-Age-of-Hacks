package io.github.securekit.core;

/**
 * 카운터 저장소(Redis 등)에 닿지 않을 때. 기본은 막는다(fail-closed) → 503.
 * 풀려면 설정 secure-kit.store.fail-open: true 를 명시한다.
 */
public class StoreUnavailableException extends SecureKitException {
    public StoreUnavailableException() {
        super(503, KitMessages.forStatus(503));
    }
}
