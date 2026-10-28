export const rolesById = Object.freeze({
 1: 'ADMIN', 2: 'LECTURER', 3: 'STUDENT', 4: 'ACCOUNTANT',
 5: 'IT_SUPPORT', 6: 'MANAGER', 7: 'HR'
});

export const dashboardRoles = {
 ADMIN: {slug: 'admin', label: 'Quản trị viên', category: 'QUẢN TRỊ HỆ THỐNG'},
 LECTURER: {slug: 'lecturer', label: 'Giảng viên', category: 'GIẢNG DẠY & ĐÀO TẠO'},
 STUDENT: {slug: 'student', label: 'Học viên', category: 'HÀNH TRÌNH HỌC TẬP'},
 ACCOUNTANT: {slug: 'accountant', label: 'Kế toán', category: 'TÀI CHÍNH ĐÀO TẠO'},
 IT_SUPPORT: {slug: 'it-support', label: 'IT Support', category: 'THIẾT BỊ & HỖ TRỢ'},
 MANAGER: {slug: 'manager', label: 'Quản lý học viên', category: 'ĐIỀU PHỐI ĐÀO TẠO'},
 HR: {slug: 'hr', label: 'Tuyển dụng', category: 'TUYỂN SINH & TƯ VẤN'}
};

export function roleError(user) {
 if (!Number.isInteger(user?.roleId) || !Object.hasOwn(rolesById, user.roleId)) {
  return 'role_id không hợp lệ. Tài khoản phải được gán role_id từ 1 đến 7. Vui lòng liên hệ quản trị viên.';
 }
 if (user.role !== rolesById[user.roleId]) {
  return `Mã vai trò không khớp với role_id ${user.roleId}. Mã vai trò yêu cầu là ${rolesById[user.roleId]}. Vui lòng liên hệ quản trị viên.`;
 }
 return '';
}

export function resolveRole(user) {
 return roleError(user) ? null : rolesById[user.roleId];
}

export function dashboardPath(user) {
 const role = resolveRole(user);
 return role ? '/dashboard/' + dashboardRoles[role].slug : '/dashboard';
}

export function workspaceRoute(path, user) {
 const canonical = resolveRole(user);
 if (!canonical) return 'unsupported';
 if (['/', '/login', '/dashboard'].includes(path)) return 'redirect';
 if (path === dashboardPath(user)) return 'dashboard';
 if (path === '/profile') return 'profile';
 if (path === '/admin/users') return canonical === 'ADMIN' ? 'users' : 'forbidden';
 if (path === '/recruitment') return ['HR', 'MANAGER'].includes(canonical) ? 'recruitment' : 'forbidden';
 if (path.startsWith('/dashboard/')) return 'forbidden';
 return 'missing';
}
