package com.erhms.user.dto;

public record UserView(Long id, String email, String fullName, String role, Long roleId, String phone) {
}
