package com.erhms.admin.dto;

import java.util.List;

public record AdminAccountPage(List<AdminAccountView> items, long total, int page, int size, int totalPages) {
}
