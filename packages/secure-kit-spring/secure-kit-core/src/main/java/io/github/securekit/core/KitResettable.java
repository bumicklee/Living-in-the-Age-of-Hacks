package io.github.securekit.core;

/** 랩 전용 POST /_kit/reset 때 메모리 상태를 처음으로 돌리는 부품·예제 저장소 */
public interface KitResettable {
    void reset();
}
