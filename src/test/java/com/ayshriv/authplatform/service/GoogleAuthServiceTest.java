package com.ayshriv.authplatform.service;

import com.ayshriv.authplatform.dto.AuthResponse;
import com.ayshriv.authplatform.dto.FederatedIdentityResponse;
import com.ayshriv.authplatform.entity.FederatedIdentity;
import com.ayshriv.authplatform.entity.Role;
import com.ayshriv.authplatform.entity.User;
import com.ayshriv.authplatform.entity.UserStatus;
import com.ayshriv.authplatform.exception.BadRequestException;
import com.ayshriv.authplatform.exception.ForbiddenException;
import com.ayshriv.authplatform.exception.ResourceAlreadyExistException;
import com.ayshriv.authplatform.repository.FederatedIdentityRepository;
import com.ayshriv.authplatform.repository.RoleRepository;
import com.ayshriv.authplatform.repository.UserRepository;
import com.ayshriv.authplatform.security.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private FederatedIdentityRepository federatedIdentityRepository;
    @Mock
    private GoogleTokenVerifier googleTokenVerifier;
    @Mock
    private AuthenticationResultService authenticationResultService;
    @Mock
    private EmailSender emailSender;
    @Mock
    private AuditService auditService;
    @Mock
    private RateLimiter rateLimiter;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private OAuthExchangeCodeService oAuthExchangeCodeService;

    private GoogleAuthService googleAuthService;

    @BeforeEach
    void setUp() {
        googleAuthService = new GoogleAuthService(
                userRepository,
                roleRepository,
                federatedIdentityRepository,
                googleTokenVerifier,
                authenticationResultService,
                emailSender,
                auditService,
                rateLimiter,
                passwordEncoder,
                oAuthExchangeCodeService
        );
    }

    @Test
    @DisplayName("Scenario 1: Existing federated identity logs in successfully")
    void processGooglePayload_Scenario1_ExistingFederatedIdentity() {
        GoogleIdTokenPayload payload = new GoogleIdTokenPayload("sub-1", "user@example.com", true, "John", "Doe", null, null);

        User user = new User(1L);
        user.setEmail("user@example.com");
        user.setStatus(UserStatus.ACTIVE);

        FederatedIdentity identity = new FederatedIdentity(user, GoogleAuthService.PROVIDER_GOOGLE, "sub-1", "user@example.com");

        when(federatedIdentityRepository.findByProviderAndProviderSubject(GoogleAuthService.PROVIDER_GOOGLE, "sub-1"))
                .thenReturn(Optional.of(identity));

        User result = googleAuthService.processGooglePayload(payload);

        assertThat(result).isEqualTo(user);
        verify(userRepository).save(user);
        verify(emailSender, never()).sendAccountLinkedEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Scenario 2: Auto-link to existing verified local account and notify by email")
    void processGooglePayload_Scenario2_AutoLinkVerifiedLocalAccount() {
        GoogleIdTokenPayload payload = new GoogleIdTokenPayload("sub-2", "verified@example.com", true, "Alice", "Smith", null, null);

        User localUser = new User(2L);
        localUser.setEmail("verified@example.com");
        localUser.setFirstName("Alice");
        localUser.setEmailVerified(true);
        localUser.setStatus(UserStatus.ACTIVE);

        when(federatedIdentityRepository.findByProviderAndProviderSubject(GoogleAuthService.PROVIDER_GOOGLE, "sub-2"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("verified@example.com"))
                .thenReturn(Optional.of(localUser));

        User result = googleAuthService.processGooglePayload(payload);

        assertThat(result).isEqualTo(localUser);
        verify(federatedIdentityRepository).save(any(FederatedIdentity.class));
        verify(emailSender).sendAccountLinkedEmail(eq("verified@example.com"), eq("Alice"), eq("Google"));
    }

    @Test
    @DisplayName("Scenario 3: Unverified local account blocks linking to prevent pre-account takeover")
    void processGooglePayload_Scenario3_BlockUnverifiedLocalAccount() {
        GoogleIdTokenPayload payload = new GoogleIdTokenPayload("sub-3", "unverified@example.com", true, "Bob", "Brown", null, null);

        User unverifiedUser = new User(3L);
        unverifiedUser.setEmail("unverified@example.com");
        unverifiedUser.setEmailVerified(false);
        unverifiedUser.setStatus(UserStatus.ACTIVE);

        when(federatedIdentityRepository.findByProviderAndProviderSubject(GoogleAuthService.PROVIDER_GOOGLE, "sub-3"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("unverified@example.com"))
                .thenReturn(Optional.of(unverifiedUser));

        assertThatThrownBy(() -> googleAuthService.processGooglePayload(payload))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("not verified");

        verify(federatedIdentityRepository, never()).save(any(FederatedIdentity.class));
        verify(emailSender, never()).sendAccountLinkedEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Scenario 4: No existing user creates new user with hasPassword=false and emailVerified=true")
    void processGooglePayload_Scenario4_CreateNewUser() {
        GoogleIdTokenPayload payload = new GoogleIdTokenPayload("sub-4", "newuser@example.com", true, "Charlie", "Chaplin", null, null);

        when(federatedIdentityRepository.findByProviderAndProviderSubject(GoogleAuthService.PROVIDER_GOOGLE, "sub-4"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("newuser@example.com"))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("unusable-hash");
        when(roleRepository.findByName(Role.ROLE_USER)).thenReturn(Optional.of(new Role(Role.ROLE_USER)));

        User createdUser = new User(4L);
        createdUser.setEmail("newuser@example.com");
        createdUser.setEmailVerified(true);
        createdUser.setHasPassword(false);
        when(userRepository.save(any(User.class))).thenReturn(createdUser);

        User result = googleAuthService.processGooglePayload(payload);

        assertThat(result.isEmailVerified()).isTrue();
        assertThat(result.isHasPassword()).isFalse();
        verify(federatedIdentityRepository).save(any(FederatedIdentity.class));
    }

    @Test
    @DisplayName("Unlink Google account succeeds when user has password set")
    void unlinkGoogleAccount_Success_WithPassword() {
        User user = new User(5L);
        user.setHasPassword(true);

        FederatedIdentity identity = new FederatedIdentity(user, GoogleAuthService.PROVIDER_GOOGLE, "sub-5", "user@example.com");

        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(federatedIdentityRepository.findByUserIdAndProvider(5L, GoogleAuthService.PROVIDER_GOOGLE))
                .thenReturn(Optional.of(identity));
        when(federatedIdentityRepository.countByUserId(5L)).thenReturn(1L);

        googleAuthService.unlinkGoogleAccount(5L);

        verify(federatedIdentityRepository).delete(identity);
    }

    @Test
    @DisplayName("Unlink Google account blocked when it is the only login method (no password)")
    void unlinkGoogleAccount_Blocked_WhenLastLoginMethod() {
        User user = new User(6L);
        user.setHasPassword(false);

        FederatedIdentity identity = new FederatedIdentity(user, GoogleAuthService.PROVIDER_GOOGLE, "sub-6", "user@example.com");

        when(userRepository.findById(6L)).thenReturn(Optional.of(user));
        when(federatedIdentityRepository.findByUserIdAndProvider(6L, GoogleAuthService.PROVIDER_GOOGLE))
                .thenReturn(Optional.of(identity));
        when(federatedIdentityRepository.countByUserId(6L)).thenReturn(1L);

        assertThatThrownBy(() -> googleAuthService.unlinkGoogleAccount(6L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("last remaining login method");

        verify(federatedIdentityRepository, never()).delete(any(FederatedIdentity.class));
    }

    @Test
    @DisplayName("Link Google account rejects if Google sub is already linked to another user")
    void linkGoogleAccount_ConflictWithOtherUser() {
        User user = new User(7L);
        User otherUser = new User(8L);

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(googleTokenVerifier.verify("token-abc"))
                .thenReturn(new GoogleIdTokenPayload("sub-other", "other@example.com", true, "Other", "User", null, null));

        FederatedIdentity otherIdentity = new FederatedIdentity(otherUser, GoogleAuthService.PROVIDER_GOOGLE, "sub-other", "other@example.com");
        when(federatedIdentityRepository.findByProviderAndProviderSubject(GoogleAuthService.PROVIDER_GOOGLE, "sub-other"))
                .thenReturn(Optional.of(otherIdentity));

        assertThatThrownBy(() -> googleAuthService.linkGoogleAccount(7L, "token-abc"))
                .isInstanceOf(ResourceAlreadyExistException.class);
    }
}
