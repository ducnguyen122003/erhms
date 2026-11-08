package com.erhms.workspace;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/master-data/screens")
public class MasterDataController {
    final MasterDataService service;final ScreenCatalog catalog;
    public MasterDataController(MasterDataService service,ScreenCatalog catalog){this.service=service;this.catalog=catalog;}
    @GetMapping({"/{code}","/{code}/{id}"})
    public Map<String,Object> view(@PathVariable String code,@PathVariable(required=false) String id,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="0") int page){return service.view(code,id,q,status,page);}
    @PostMapping("/{code}")
    public Map<String,Object> create(@PathVariable String code,@RequestBody Map<String,Object> input,HttpServletRequest request){audit(request,code,null,"CREATE");var result=service.save(code,null,input);request.setAttribute("admin.targetId",result.get("id"));return result;}
    @PutMapping("/{code}/{id}")
    public Map<String,Object> update(@PathVariable String code,@PathVariable String id,@RequestBody Map<String,Object> input,HttpServletRequest request){audit(request,code,id,"UPDATE");return service.save(code,id,input);}
    @DeleteMapping("/{code}/{id}")
    public Map<String,Object> delete(@PathVariable String code,@PathVariable String id,@RequestBody Map<String,Object> input,HttpServletRequest request){audit(request,code,id,"DELETE");return service.delete(code,id,input);}
    private void audit(HttpServletRequest request,String code,String id,String action){request.setAttribute("workflow.action","MASTER_"+action);request.setAttribute("workflow.label",switch(action){case "CREATE"->"Tạo: ";case "UPDATE"->"Cập nhật: ";default->"Xóa: ";}+catalog.get(code).title());request.setAttribute("admin.targetId",id);}
}
