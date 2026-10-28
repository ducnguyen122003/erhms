export function resetPasswordError(password, confirmation) {
 if (!password.trim() || [...password].length < 8) return 'Mật khẩu mới phải có ít nhất 8 ký tự.';
 if (new TextEncoder().encode(password).length > 72) return 'Mật khẩu mới không được vượt quá 72 byte (ký tự có dấu có thể chiếm nhiều byte).';
 if (password !== confirmation) return 'Mật khẩu xác nhận không khớp.';
 return '';
}
