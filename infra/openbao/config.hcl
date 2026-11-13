# 다온마켓 랩 · OpenBao 설정(아키텍처 A4, 수업 5-2). 랩 안 망에서만 쓴다.
# - 저장: 파일(컨테이너를 지워도 bao-data 볼륨에 남는다). 개발 모드(-dev)는 재시작하면 키가 사라져서 쓰지 않는다.
# - TLS를 끈 것은 랩 내부 망이기 때문이다. 회사에서는 반드시 TLS를 켠다(5-5와 같은 원리).
ui            = true
disable_mlock = true

storage "file" {
  path = "/openbao/file"
}

listener "tcp" {
  address     = "0.0.0.0:8200"
  tls_disable = true
}

api_addr = "http://openbao:8200"

# 감사 로그: 서명·풀기 호출이 모두 남는다(5-3·8장 탐지 재료). 요청 값은 해시로만 남는다.
# OpenBao 2.x는 감사 장치를 API가 아니라 설정 파일로만 켠다.
audit "file" "daon" {
  options {
    file_path = "/openbao/logs/audit.log"
  }
}
