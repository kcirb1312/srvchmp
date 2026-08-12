package pl.championsmp.survivalcore.auth;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class AuthManager {

    private final Set<UUID> loggedInPlayers =
            ConcurrentHashMap.newKeySet();

    private final Map<UUID, Integer> captchaMap =
            new ConcurrentHashMap<>();

    public void login(UUID playerUUID) {
        loggedInPlayers.add(playerUUID);
        captchaMap.remove(playerUUID);
    }

    public void logout(UUID playerUUID) {
        loggedInPlayers.remove(playerUUID);
        captchaMap.remove(playerUUID);
    }

    public boolean isLoggedIn(UUID playerUUID) {
        return loggedInPlayers.contains(playerUUID);
    }

    public int generateCaptcha(UUID playerUUID) {

        int captcha =
                ThreadLocalRandom.current()
                        .nextInt(1000, 10000);

        captchaMap.put(
                playerUUID,
                captcha
        );

        return captcha;
    }

    public Integer getCaptcha(UUID playerUUID) {
        return captchaMap.get(playerUUID);
    }

    public void removeCaptcha(UUID playerUUID) {
        captchaMap.remove(playerUUID);
    }

    public Map<UUID, Integer> getCaptchaMap() {
        return captchaMap;
    }

    public void clear() {
        loggedInPlayers.clear();
        captchaMap.clear();
    }
}