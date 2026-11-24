package com.erhms.workspace;

import com.erhms.admin.service.ScreenPermissionsService;
import com.erhms.user.entity.User;
import com.erhms.user.service.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static com.erhms.workspace.WorkflowData.role;

@RestController
@RequestMapping("/api/workspace/equipment-initial-requests")
public class EquipmentInitialRequestsController {
    private final EquipmentWorkflows equipment;
    private final CurrentUser current;
    private final ScreenPermissionsService permissions;

    public EquipmentInitialRequestsController(EquipmentWorkflows equipment, CurrentUser current, ScreenPermissionsService permissions) {
        this.equipment = equipment;
        this.current = current;
        this.permissions = permissions;
    }

    @GetMapping
    public Map<String,Object> candidates(@RequestParam(defaultValue="") String classId,
                                        @RequestParam(defaultValue="") String q,
                                        @RequestParam(defaultValue="0") int page) {
        return equipment.initialCandidates(classId, q, page, lecturer());
    }

    @PostMapping
    public Map<String,Object> create(@RequestBody Map<String,Object> input, HttpServletRequest request) {
        request.setAttribute("workflow.action", "EQUIPMENT_INITIAL_REQUESTS_CREATE");
        request.setAttribute("workflow.label", "Tạo yêu cầu cấp thiết bị ban đầu cho học viên");
        var result = equipment.initialRequests(input, lecturer());
        request.setAttribute("admin.targetId", result.get("classId"));
        request.setAttribute("admin.changeNote", result.get("notice"));
        return result;
    }

    private User lecturer() {
        User actor = current.get();
        role(actor, "LECTURER");
        permissions.require(actor, "ListEquipmentRequest");
        permissions.require(actor, "AddEquipmentRequest");
        return actor;
    }
}
