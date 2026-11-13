vcl 4.1;
# 다온마켓 A7 · Varnish 캐시 설정 [회사 설정] (11-3에서 A7과 함께 들어옴 = 11-4 시작 지점)
#
# 성능 팀 메모(가상): 「상품·회원 API가 느리다. /api/ GET은 60초 동안 캐시에서 준다. 로그인 여부와 상관없이」
#   - Varnish 기본 동작은 Authorization·쿠키가 있는 요청을 캐시에 넣지 않고(pass), 오리진이 no-store라고 하면 저장하지 않는다.
#   - 아래 두 줄(☆)이 그 기본을 덮는다. 캐시 키는 「주소(+Host)」뿐이라 누가 요청했는지가 들어가지 않는다.
# 고치기는 11-4: 인증된 요청은 pass, 오리진 Cache-Control을 따름, 오류 응답은 저장하지 않음.

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
    if (req.method == "GET" || req.method == "HEAD") {
        return (hash);                       # ☆ Authorization·쿠키가 있어도 캐시에서 찾는다
    }
    set req.http.X-Daon-Cache = "PASS";
    return (pass);
}

sub vcl_backend_fetch {
    set bereq.url = regsub(bereq.url, "^/api/", "/");     # 앱 주소에는 /api/가 없다(고객 웹 nginx와 같은 규칙)
    unset bereq.http.X-Daon-Cache;
}

sub vcl_backend_response {
    if (bereq.method == "GET" || bereq.method == "HEAD") {
        unset beresp.http.Set-Cookie;
        set beresp.ttl = 60s;                # ☆ 오리진의 Cache-Control(no-store 등)과 상태 코드를 보지 않고 60초
        return (deliver);
    }
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
