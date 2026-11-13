vcl 4.1;
# 다온마켓 A7 · Varnish 캐시 설정 (11-4 정답)
#
# 캐시 키(주소 + Host)에는 「누가 요청했는지」가 없다. 그래서 두 겹으로 막는다.
#   ① 캐시(이 파일): 로그인한 요청(Authorization·쿠키)은 캐시를 거치지 않는다(pass). 오리진이 private·no-store·no-cache라고
#      하거나 저장 기간(max-age)을 밝히지 않은 응답은 저장하지 않는다. 오류 응답(4xx·5xx)은 저장하지 않는다
#   ② 오리진(mall-portal DaonCacheHeaderFilter): 로그인한 요청의 응답에 Cache-Control: private, no-store,
#      공개 상품 목록에는 public, max-age=60. 캐시 여부는 오리진이 정한다
# 한 겹만 고치면 다음에 누가 다른 겹을 바꿀 때 다시 뚫린다.

backend portal {
    .host = "mall-portal";
    .port = "8085";
    .connect_timeout = 2s;
    .first_byte_timeout = 30s;
}

sub vcl_recv {
    if (req.url !~ "^/api/") {
        return (synth(404));
    }
    if (req.method != "GET" && req.method != "HEAD") {
        set req.http.X-Daon-Cache = "PASS";
        return (pass);
    }
    if (req.http.Authorization || req.http.Cookie) {
        set req.http.X-Daon-Cache = "PASS";  # 로그인한 요청은 캐시를 거치지 않는다
        return (pass);
    }
    return (hash);
}

sub vcl_backend_fetch {
    set bereq.url = regsub(bereq.url, "^/api/", "/");
    unset bereq.http.X-Daon-Cache;
}

sub vcl_backend_response {
    if (beresp.status >= 400) {
        set beresp.ttl = 0s;                 # 오류 한 번이 모든 사용자에게 퍼지지 않게(캐시 오염·CPDoS)
        set beresp.uncacheable = true;
        return (deliver);
    }
    if (beresp.http.Cache-Control ~ "(?i)(private|no-store|no-cache)" || beresp.http.Set-Cookie) {
        set beresp.ttl = 0s;                 # 오리진이 저장하지 말라고 했다
        set beresp.uncacheable = true;
        return (deliver);
    }
    if (beresp.http.Cache-Control !~ "(?i)max-age=[1-9]") {
        set beresp.ttl = 0s;                 # 저장 기간을 밝히지 않은 응답은 저장하지 않는다(기본값 120초를 쓰지 않음)
        set beresp.uncacheable = true;
        return (deliver);
    }
    return (deliver);                        # 저장 기간은 Varnish가 오리진의 max-age에서 이미 정했다
}

sub vcl_deliver {
    if (req.http.X-Daon-Cache == "PASS") {
        set resp.http.X-Cache = "PASS";
    } elsif (obj.hits > 0) {
        set resp.http.X-Cache = "HIT";
    } else {
        set resp.http.X-Cache = "MISS";
    }
    unset resp.http.Via;
    unset resp.http.X-Varnish;
}
