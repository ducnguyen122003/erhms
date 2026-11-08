package com.erhms.workspace;

import org.springframework.web.bind.annotation.*;
import java.util.*;
import static com.erhms.workspace.WorkflowData.*;

@RestController
@RequestMapping("/api/public/training-courses")
public class PublicTrainingController {
    final WorkflowData d;
    public PublicTrainingController(WorkflowData d){this.d=d;}
    private static final String SELECT="SELECT id,code,name,COALESCE((SELECT p.name FROM m_profession p WHERE p.id=training_courses.profession_id),profession) AS profession,duration_months,total_sessions,tuition_fee,objectives,entry_requirements FROM training_courses WHERE status IN ('OPEN','ACTIVE')";
    @GetMapping public List<Map<String,Object>> courses(){return d.rows(SELECT+" ORDER BY name");}
    @GetMapping("/{id}") public Map<String,Object> course(@PathVariable UUID id){return d.one(SELECT+" AND id=?",id);}
}
