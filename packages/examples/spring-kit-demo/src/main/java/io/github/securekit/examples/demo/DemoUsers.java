package io.github.securekit.examples.demo;

import io.github.securekit.core.KitResettable;
import io.github.securekit.core.password.ConstantTimeLogin;
import io.github.securekit.web.aspect.StepUpVerifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 데모 사용자 저장소(메모리). 비밀번호는 해시로만 둔다. /_kit/reset이면 처음 해시로 돌아간다 */
@Component
public class DemoUsers implements KitResettable, StepUpVerifier {
    private final DemoProperties props;
    private final ConstantTimeLogin login;
    private final Map<String, String> hashes = new ConcurrentHashMap<>();

    public DemoUsers(DemoProperties props, ConstantTimeLogin login) {
        this.props = props;
        this.login = login;
        reset();
    }

    /** 없는 아이디면 null(그래도 ConstantTimeLogin이 같은 비용으로 비교한다) */
    public String hashOf(String username) {
        return username == null ? null : hashes.get(username);
    }

    public boolean exists(String username) {
        return hashOf(username) != null;
    }

    public List<String> roles(String username) {
        for (DemoProperties.User u : props.getUsers()) {
            if (u.getUsername().equals(username)) return u.getRoles();
        }
        return List.of();
    }

    public void changePassword(String username, String newPassword) {
        hashes.put(username, login.hash(newPassword));
    }

    @Override
    public boolean verify(String subject, String password) {
        return login.matches(hashOf(subject), password);
    }

    @Override
    public void reset() {
        hashes.clear();
        for (DemoProperties.User u : props.getUsers()) hashes.put(u.getUsername(), u.getHash());
    }
}
