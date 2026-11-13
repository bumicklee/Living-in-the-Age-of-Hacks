package io.github.securekit.web.aspect;

/** &#64;RequireStepUp이 부르는 비밀번호 확인. 앱이 자기 사용자 저장소로 구현해 빈으로 둔다 */
public interface StepUpVerifier {
    boolean verify(String subject, String password);
}
