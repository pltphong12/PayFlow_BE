package com.payflow.user.service;

import com.payflow.common.event.UserRegistered;
import com.payflow.common.exception.BusinessException;
import com.payflow.common.jwt.JwtProperties;
import com.payflow.common.jwt.JwtUtil;
import com.payflow.user.dto.request.LoginRequest;
import com.payflow.user.dto.request.RefreshTokenRequest;
import com.payflow.user.dto.request.RegisterRequest;
import com.payflow.user.dto.response.LoginResponse;
import com.payflow.user.dto.response.RegisterResponse;
import com.payflow.user.entity.RefreshToken;
import com.payflow.user.entity.User;
import com.payflow.user.entity.UserRole;
import com.payflow.user.entity.UserStatus;
import com.payflow.user.kafka.producer.UserEventProducer;
import com.payflow.user.repository.RefreshTokenRepository;
import com.payflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String TEST_SECRET =
            "test-secret-key-at-least-256-bits-long-for-hs256-algorithm";

    @Mock
    private UserEventProducer userEventProducer;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private AuthService authService;

    private JwtUtil jwtUtil;
    private JwtProperties jwtProperties;
    private TokenHashService tokenHashService;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties(TEST_SECRET, 15, 10080);
        jwtUtil = new JwtUtil(jwtProperties);
        tokenHashService = new TokenHashService();
        ReflectionTestUtils.setField(authService, "jwtUtil", jwtUtil);
        ReflectionTestUtils.setField(authService, "jwtProperties", jwtProperties);
        ReflectionTestUtils.setField(authService, "tokenHashService", tokenHashService);
    }

    @Test
    void should_register_new_user_and_publish_user_registered_event() {
        RegisterRequest request = mockRegisterRequest("new@payflow.vn", "Password123", "New User");
        User saved = activeUser(UUID.randomUUID(), "new@payflow.vn", "hashed-password", "New User");

        when(userRepository.existsByEmail("new@payflow.vn")).thenReturn(false);
        when(passwordEncoder.encode("Password123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenReturn(saved);

        RegisterResponse response = authService.register(request);

        assertThat(response.getEmail()).isEqualTo("new@payflow.vn");
        assertThat(response.getRole()).isEqualTo(UserRole.USER);

        ArgumentCaptor<UserRegistered> eventCaptor = ArgumentCaptor.forClass(UserRegistered.class);
        verify(userEventProducer).publishUserRegistered(eventCaptor.capture());
        assertThat(eventCaptor.getValue().userId()).isEqualTo(saved.getId());
        assertThat(eventCaptor.getValue().email()).isEqualTo("new@payflow.vn");
    }

    @Test
    void should_throw_conflict_when_email_already_exists() {
        RegisterRequest request = mockRegisterRequest("dup@payflow.vn", null, null);
        when(userRepository.existsByEmail("dup@payflow.vn")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(userRepository, never()).save(any());
        verify(userEventProducer, never()).publishUserRegistered(any());
    }

    @Test
    void should_throw_unauthorized_when_login_email_not_found() {
        LoginRequest request = mockLoginRequest("missing@payflow.vn", null);
        when(userRepository.findByEmail("missing@payflow.vn")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(businessException.getMessage()).isEqualTo("Invalid email or password");
                });
    }

    @Test
    void should_throw_unauthorized_when_login_password_is_wrong() {
        LoginRequest request = mockLoginRequest("user@payflow.vn", "WrongPassword");
        User user = activeUser(UUID.randomUUID(), "user@payflow.vn", "hashed-password", "User");

        when(userRepository.findByEmail("user@payflow.vn")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void should_login_successfully_and_save_refresh_token() {
        LoginRequest request = mockLoginRequest("user@payflow.vn", "Password123");
        User user = activeUser(UUID.randomUUID(), "user@payflow.vn", "hashed-password", "User");

        when(userRepository.findByEmail("user@payflow.vn")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123", "hashed-password")).thenReturn(true);

        LoginResponse response = authService.login(request);

        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(900L);
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getRefreshExpiresIn()).isEqualTo(604800L);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void should_throw_unauthorized_when_refresh_token_is_invalid() {
        RefreshTokenRequest request = mockRefreshTokenRequest("invalid-refresh-token");
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void should_logout_and_revoke_refresh_token() {
        String rawRefreshToken = jwtUtil.generateRefreshToken();
        RefreshTokenRequest request = mockRefreshTokenRequest(rawRefreshToken);
        User user = activeUser(UUID.randomUUID(), "user@payflow.vn", "hashed-password", "User");
        RefreshToken stored = new RefreshToken(user, tokenHashService.hash(rawRefreshToken),
                Instant.now().plusSeconds(3600));

        when(refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHashService.hash(rawRefreshToken)))
                .thenReturn(Optional.of(stored));

        authService.logout(request);

        assertThat(stored.isRevoked()).isTrue();
    }

    private static RegisterRequest mockRegisterRequest(String email, String password, String fullName) {
        RegisterRequest request = mock(RegisterRequest.class);
        when(request.getEmail()).thenReturn(email);
        if (password != null) {
            when(request.getPassword()).thenReturn(password);
        }
        if (fullName != null) {
            when(request.getFullName()).thenReturn(fullName);
        }
        return request;
    }

    private static LoginRequest mockLoginRequest(String email, String password) {
        LoginRequest request = mock(LoginRequest.class);
        when(request.getEmail()).thenReturn(email);
        if (password != null) {
            when(request.getPassword()).thenReturn(password);
        }
        return request;
    }

    private static RefreshTokenRequest mockRefreshTokenRequest(String refreshToken) {
        RefreshTokenRequest request = mock(RefreshTokenRequest.class);
        when(request.getRefreshToken()).thenReturn(refreshToken);
        return request;
    }

    private static User activeUser(UUID id, String email, String passwordHash, String fullName) {
        User user = new User(email, passwordHash, fullName, UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "createdAt", Instant.parse("2026-07-20T00:00:00Z"));
        return user;
    }
}
