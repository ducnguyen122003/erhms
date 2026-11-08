package com.erhms.admin.dto;

import java.util.List;
import java.util.Map;

public record AdminAccountPage(List<AdminAccountView> items, long total, int page, int size, int totalPages, Map<String,Long> stats) {
}
