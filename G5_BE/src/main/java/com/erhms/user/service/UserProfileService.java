package com.erhms.user.service;

import com.erhms.user.dto.ProfileInput;
import com.erhms.user.dto.UserView;
import com.erhms.user.entity.User;
import com.erhms.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {
    private final CurrentUser current;
    private final UserRepository users;

    public UserProfileService(CurrentUser current, UserRepository users) {
        this.current = current;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public UserView get() {
        return view(current.get());
    }

    @Transactional
    public UserView update(ProfileInput input) {
        // Use the same row lock as password changes so a profile save cannot overwrite a newer hash.
        var user = current.getForUpdate();
        user.fullName = input.fullName();
        user.phone = input.phone();
        users.saveAndFlush(user);
        return view(user);
    }

    private static UserView view(User user) {
        return new UserView(user.id, user.email, user.fullName, user.getRoleCode(), user.getRoleId(), user.phone);
    }
}
