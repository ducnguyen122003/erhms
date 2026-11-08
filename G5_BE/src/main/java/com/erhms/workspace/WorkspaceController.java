package com.erhms.workspace;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.Map;

@RestController
@RequestMapping("/api/workspace/screens")
public class WorkspaceController {
    final WorkspaceService service;final ScreenCatalog catalog;
    public WorkspaceController(WorkspaceService service,ScreenCatalog catalog){this.service=service;this.catalog=catalog;}
    @GetMapping({"/{code}","/{code}/{id}"})
    public Map<String,Object> view(@PathVariable String code,@PathVariable(required=false) String id,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="") String from,@RequestParam(defaultValue="") String to){return service.view(code,id,q,status,page,from,to);}
    @PostMapping({"/{code}","/{code}/{id}"})
    public Map<String,Object> save(@PathVariable String code,@PathVariable(required=false) String id,@RequestBody Map<String,Object> input,HttpServletRequest request){
        audit(request,code,id,input);Map<String,Object> result=service.save(code,id,input);request.setAttribute("admin.targetId",result.get("id"));return result;
    }
    @PostMapping("/{code}/{id}/actions/{action}")
    public Map<String,Object> action(@PathVariable String code,@PathVariable String id,@PathVariable String action,@RequestBody Map<String,Object> input,HttpServletRequest request){audit(request,code,id,input);return service.action(code,id,action,input);}
    void audit(HttpServletRequest request,String code,String id,Map<String,Object> input){
        request.setAttribute("workflow.action","WORKFLOW_"+code.toUpperCase(java.util.Locale.ROOT));request.setAttribute("workflow.label",catalog.get(code).title());request.setAttribute("admin.targetId",id);
        if(input.get("changeNote") instanceof String note)request.setAttribute("admin.changeNote",note);
    }
}
