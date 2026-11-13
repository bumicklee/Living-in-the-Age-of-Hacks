package io.github.securekit.examples.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/** 예제 전용 설정 kit-demo.* (부품 설정이 아니다) */
@ConfigurationProperties("kit-demo")
public class DemoProperties {
    private String flakyUrl;
    private List<User> users = new ArrayList<>();

    public String getFlakyUrl() { return flakyUrl; }
    public void setFlakyUrl(String flakyUrl) { this.flakyUrl = flakyUrl; }
    public List<User> getUsers() { return users; }
    public void setUsers(List<User> users) { this.users = users; }

    public static class User {
        private String username;
        private List<String> roles = new ArrayList<>();
        private String hash;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public List<String> getRoles() { return roles; }
        public void setRoles(List<String> roles) { this.roles = roles; }
        public String getHash() { return hash; }
        public void setHash(String hash) { this.hash = hash; }
    }
}
