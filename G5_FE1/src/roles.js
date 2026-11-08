import {canScreen,screenNavigation,screenRoute} from './screens/access.js';

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

export function workspaceRoute(path, user, functions = null) {
 const canonical = resolveRole(user);
 if (!canonical) return 'unsupported';
 const has = code => functions ? functions.includes(code) : ['DASHBOARD','PROFILE'].includes(code) || (canonical === 'ADMIN' && ['USER_ACCOUNTS','ROLE_PERMISSIONS','SYSTEM_CONFIG','SYSTEM_AUDIT_LOG'].includes(code)) || (['HR','MANAGER'].includes(canonical) && code === 'RECRUITMENT');
 const route=screenRoute(path);
 if(route)return canScreen(route.screen.code,user,functions||[])?'screen':'forbidden';
 if (['/', '/login', '/dashboard'].includes(path)) return 'redirect';
 if (path === dashboardPath(user)) return has('DASHBOARD') ? 'dashboard' : 'forbidden';
 if (path === '/profile') return has('PROFILE') ? 'profile' : 'forbidden';
 if (path === '/admin/users') return has('USER_ACCOUNTS') ? 'users' : 'forbidden';
 if (path === '/admin/users/new') return has('USER_ACCOUNTS') ? 'user-create' : 'forbidden';
 if (/^\/admin\/users\/[1-9][0-9]*\/view$/.test(path)) return has('USER_ACCOUNTS') ? 'user-detail' : 'forbidden';
 if (/^\/admin\/users\/[1-9][0-9]*\/edit$/.test(path)) return has('USER_ACCOUNTS') ? 'user-edit' : 'forbidden';
 if (path === '/admin/permissions') return has('ROLE_PERMISSIONS') ? 'permissions' : 'forbidden';
 if (path === '/admin/settings') return has('SYSTEM_CONFIG') ? 'settings' : 'forbidden';
 if (path === '/admin/audit-logs') return has('SYSTEM_AUDIT_LOG') ? 'audit' : 'forbidden';
 if (path === '/recruitment') return ['HR', 'MANAGER'].includes(canonical) && has('RECRUITMENT') ? 'recruitment' : 'forbidden';
 if (path === '/access-denied') return 'forbidden';
 if (path.startsWith('/dashboard/')) return 'forbidden';
 return 'missing';
}

export function firstAllowedPath(user, functions) {
 if (functions.includes('DASHBOARD')) return dashboardPath(user);
 for (const [code,path] of [['USER_ACCOUNTS','/admin/users'],['ROLE_PERMISSIONS','/admin/permissions'],['SYSTEM_CONFIG','/admin/settings'],['SYSTEM_AUDIT_LOG','/admin/audit-logs'],['PROFILE','/profile'],['RECRUITMENT','/recruitment']]) {
  if (functions.includes(code)) return path;
 }
 return screenNavigation(user,functions)[0]?.path||'/access-denied';
}
