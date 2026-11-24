package com.erhms.workspace;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static com.erhms.workspace.WorkflowData.invalid;

final class AssetCodes {
    private static final Pattern NUMBER_SUFFIX = Pattern.compile("^(.*?)([0-9]+)$");

    private AssetCodes() {}

    static List<String> sequence(String code, int quantity) {
        var match = NUMBER_SUFFIX.matcher(code);
        String prefix = code;
        BigInteger first = BigInteger.ONE;
        int width = 3;
        if (match.matches()) {
            prefix = match.group(1);
            first = new BigInteger(match.group(2));
            width = Math.max(width, match.group(2).length());
        }
        if (prefix.codePoints().noneMatch(Character::isLetter) || first.signum() <= 0)
            throw invalid("code", "Nhập mã có tiền tố chữ và số thứ tự từ 001, ví dụ BK001 hoặc BK");

        List<String> codes = new ArrayList<>(quantity);
        for (int i = 0; i < quantity; i++) {
            String digits = first.add(BigInteger.valueOf(i)).toString();
            String next = prefix + "0".repeat(Math.max(0, width - digits.length())) + digits;
            if (next.length() > 40) throw invalid("code", "Mã thiết bị trong dải vượt quá 40 ký tự");
            codes.add(next);
        }
        return codes;
    }
}
