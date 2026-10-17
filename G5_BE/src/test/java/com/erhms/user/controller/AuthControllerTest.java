package com.erhms.user.controller;

import com.erhms.user.dto.PasswordChangeInput;
import com.erhms.user.dto.ProfileInput;
import com.erhms.user.dto.UserView;
import com.erhms.user.service.UserProfileService;
import com.erhms.user.service.PasswordChangeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthControllerTest {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void profileUpdateFinishingAfterLogoutCannotCreateANewAuthenticatedSession() {
        var profiles = mock(UserProfileService.class);
        var controller = new AuthController(profiles, mock(PasswordChangeService.class));
        var request = new MockHttpServletRequest();
        var session = (MockHttpSession) request.getSession(true);
        var input = new ProfileInput("Updated User", null);
        var profile = new UserView(7L, "original@example.com", input.fullName(), "USER", 1L, null);
        when(profiles.update(input)).thenAnswer(invocation -> {
            // Model logout invalidating the existing session while the database update is in flight.
            session.invalidate();
            return profile;
        });

        assertEquals(profile, controller.updateMe(input));

        assertTrue(session.isInvalid());
        assertNull(request.getSession(false), "A completed profile update must not recreate a logged-out session");
        verify(profiles).update(input);
    }

    @Test
    void passwordChangeFinishingAfterLogoutCannotCreateANewAuthenticatedSession() {
        var passwords = mock(PasswordChangeService.class);
        var controller = new AuthController(mock(UserProfileService.class), passwords);
        var request = new MockHttpServletRequest();
        var session = (MockHttpSession) request.getSession(true);
        var input = new PasswordChangeInput("CurrentPassword", "NewPassword", "NewPassword");
        doAnswer(invocation -> {
            session.invalidate();
            SecurityContextHolder.clearContext();
            return null;
        }).when(passwords).change(input);

        controller.changePassword(input);

        assertTrue(session.isInvalid());
        assertNull(request.getSession(false));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(passwords).change(input);
    }
}
