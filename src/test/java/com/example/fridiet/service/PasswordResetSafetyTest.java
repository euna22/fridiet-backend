package com.example.fridiet.service;

import com.example.fridiet.entity.User;
import com.example.fridiet.repository.UserRepository;
import com.example.fridiet.util.RedisUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordResetSafetyTest {
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final RedisUtil redis = mock(RedisUtil.class);
    private final UserService service = new UserService(users, mock(OAuthService.class), encoder, redis);

    @Test void invalidOrExpiredTokenCannotWritePassword() {
        assertThrows(IllegalArgumentException.class, () -> service.resetPasswordWithToken("invalid", "new-password"));
        verifyNoInteractions(users, encoder);
    }

    @Test void blankTokenCannotAccessRedis() {
        assertThrows(IllegalArgumentException.class, () -> service.resetPasswordWithToken(" ", "new-password"));
        verifyNoInteractions(users, encoder, redis);
    }

    @Test void consumedTokenCannotBeReused() {
        User user = mock(User.class);
        when(redis.consumeData("RESET_TOKEN:valid")).thenReturn("demo@test.com").thenReturn(null);
        when(users.findByEmail("demo@test.com")).thenReturn(Optional.of(user));
        when(user.getSocialProvider()).thenReturn("EMAIL");
        when(encoder.encode("new-password")).thenReturn("encoded");
        service.resetPasswordWithToken("valid", "new-password");
        assertThrows(IllegalArgumentException.class, () -> service.resetPasswordWithToken("valid", "different"));
        verify(user).updatePassword("encoded");
        verify(encoder, times(1)).encode(anyString());
        verify(redis, never()).getData(anyString());
    }
}
