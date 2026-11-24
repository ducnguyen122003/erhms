package com.erhms.workspace;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.erhms.workspace.WorkflowData.invalid;
import static com.erhms.workspace.WorkflowData.items;
import static com.erhms.workspace.WorkflowData.number;
import static com.erhms.workspace.WorkflowData.text;
import static com.erhms.workspace.WorkflowData.uuid;

@Service
public class StudentAccountBatchService {
    private static final int LIMIT = 20;
    private final StudentAccountsService accounts;

    public StudentAccountBatchService(StudentAccountsService accounts) {
        this.accounts = accounts;
    }

    public record RowResult(String id, String status, String message) {}

    public record Result(int createdCount, int linkedCount, int failedCount, List<RowResult> results, String notice) {}

    private record Selection(UUID id, String email, long version) {}

    // Each proxied create commits or rolls back separately, including its existing email delivery checks.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Result create(Map<String,Object> input) {
        accounts.requireCreateAccess();
        List<Selection> selections = selectedStudents(input);
        List<RowResult> results = new ArrayList<>(selections.size());
        int created = 0;
        int linked = 0;
        for (Selection selection : selections) {
            String id = selection.id().toString();
            try {
                var outcome = accounts.create(id,Map.of("expectedEmail",selection.email(),"version",selection.version()));
                boolean newAccount = Boolean.TRUE.equals(outcome.get("created"));
                if (newAccount) created++;
                else linked++;
                results.add(new RowResult(id,newAccount?"CREATED":"LINKED",outcome.get("notice").toString()));
            } catch (WorkflowException failure) {
                results.add(new RowResult(id,"FAILED",failure.getMessage()));
            } catch (DataIntegrityViolationException failure) {
                results.add(new RowResult(id,"FAILED","Dữ liệu hồ sơ vừa thay đổi hoặc bị trùng. Hãy tải lại và kiểm tra."));
            }
        }
        int failed = results.size() - created - linked;
        String notice = "Đã tạo %d tài khoản, gắn %d tài khoản hiện có, %d hồ sơ chưa xử lý được.".formatted(created,linked,failed);
        return new Result(created,linked,failed,List.copyOf(results),notice);
    }

    private List<Selection> selectedStudents(Map<String,Object> input) {
        if (!input.keySet().equals(Set.of("students"))) throw invalid("students","Chỉ gửi danh sách hồ sơ đã chọn.");
        var requested = items(input,"students");
        if (requested.isEmpty() || requested.size() > LIMIT)
            throw invalid("students","Chọn từ 1 đến "+LIMIT+" hồ sơ để cấp tài khoản.");

        List<Selection> selections = new ArrayList<>(requested.size());
        Set<UUID> ids = new HashSet<>();
        for (var row : requested) {
            if (!row.keySet().equals(Set.of("id","expectedEmail","version")))
                throw invalid("students","Mỗi hồ sơ cần có mã, email và phiên bản đã chọn.");
            UUID id = uuid(row,"id");
            if (!ids.add(id)) throw invalid("students","Danh sách có hồ sơ bị trùng.");
            selections.add(new Selection(id,text(row,"expectedEmail",190,true),number(row,"version",0,Long.MAX_VALUE)));
        }
        return selections;
    }
}
