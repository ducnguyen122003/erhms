package com.erhms.admin.service;

import com.erhms.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class SystemAuditService {
    private static final ZoneId ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
    private final JdbcTemplate jdbc;
    private final ScreenPermissionsService permissions;
    public SystemAuditService(JdbcTemplate jdbc,ScreenPermissionsService permissions) { this.jdbc=jdbc; this.permissions=permissions; }
    public record Actor(Long id,String name,String role) {}
    public record Entry(UUID id,String actorName,String actorRole,String actionCode,String actionLabel,String targetId,
                        String method,String path,String result,int status,String changeNote,Instant occurredAt) {}
    public void record(Actor actor,String action,String label,String target,String method,String path,int status,String note) {
        jdbc.update("""
                INSERT INTO system_audit_log(id,actor_id,actor_name,actor_role,action_code,action_label,target_id,
                request_method,request_path,result,status_code,change_note,occurred_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,UUID.randomUUID(),actor==null?null:actor.id(),actor==null?"Chưa xác thực":actor.name(),actor==null?null:actor.role(),
                action,label,target,method,path,status>=200&&status<400?"SUCCESS":"FAILURE",status,note,Timestamp.from(Instant.now()));
    }
    @Transactional(readOnly=true)
    public Map<String,Object> list(String q,String action,String date,int page,int size) {
        permissions.require("SYSTEM_AUDIT_LOG");
        if (page<0||size<1||size>100||q.length()>200) throw new BusinessException(HttpStatus.BAD_REQUEST,"Bộ lọc hoặc phân trang không hợp lệ");
        var args=new ArrayList<Object>();
        String where=" WHERE 1=1";
        if (!q.isBlank()) { where+=" AND (LOWER(actor_name) LIKE ? ESCAPE '\\' OR LOWER(action_label) LIKE ? ESCAPE '\\' OR LOWER(COALESCE(target_id,'')) LIKE ? ESCAPE '\\')";
            String pattern="%"+q.strip().toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%"; Collections.addAll(args,pattern,pattern,pattern); }
        if (!action.isBlank()) { where+=" AND action_code=?"; args.add(action); }
        if (!date.isBlank()) {
            try { LocalDate day=LocalDate.parse(date); where+=" AND occurred_at>=? AND occurred_at<?"; args.add(Timestamp.from(day.atStartOfDay(ZONE).toInstant())); args.add(Timestamp.from(day.plusDays(1).atStartOfDay(ZONE).toInstant())); }
            catch(DateTimeException e) { throw new BusinessException(HttpStatus.BAD_REQUEST,"Ngày lọc không hợp lệ"); }
        }
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM system_audit_log"+where,Long.class,args.toArray());
        var paged=new ArrayList<>(args); paged.add(size); paged.add((long)page*size);
        var items=jdbc.query("SELECT * FROM system_audit_log"+where+" ORDER BY occurred_at DESC,id DESC LIMIT ? OFFSET ?",(rs,n)->new Entry(
                rs.getObject("id",UUID.class),rs.getString("actor_name"),rs.getString("actor_role"),rs.getString("action_code"),rs.getString("action_label"),
                rs.getString("target_id"),rs.getString("request_method"),rs.getString("request_path"),rs.getString("result"),rs.getInt("status_code"),rs.getString("change_note"),rs.getTimestamp("occurred_at").toInstant()),paged.toArray());
        Instant start=LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant(),end=LocalDate.now(ZONE).plusDays(1).atStartOfDay(ZONE).toInstant();
        var stats=jdbc.queryForMap("""
                SELECT COUNT(*) AS "total",COALESCE(SUM(CASE WHEN result='SUCCESS' THEN 1 ELSE 0 END),0) AS "success",
                COALESCE(SUM(CASE WHEN result='FAILURE' THEN 1 ELSE 0 END),0) AS "failed",COUNT(DISTINCT actor_id) AS "actors"
                FROM system_audit_log WHERE occurred_at>=? AND occurred_at<?
                """,Timestamp.from(start),Timestamp.from(end));
        var actions=jdbc.queryForList("SELECT DISTINCT action_code AS \"code\",action_label AS \"label\" FROM system_audit_log ORDER BY \"label\"");
        return Map.of("items",items,"total",total,"page",page,"size",size,"totalPages",(total+size-1)/size,"stats",stats,"actions",actions);
    }
    @Transactional(readOnly=true)
    public Entry detail(UUID id) {
        permissions.require("SYSTEM_AUDIT_LOG");
        return jdbc.query("SELECT * FROM system_audit_log WHERE id=?",(rs,n)->new Entry(rs.getObject("id",UUID.class),rs.getString("actor_name"),rs.getString("actor_role"),
                rs.getString("action_code"),rs.getString("action_label"),rs.getString("target_id"),rs.getString("request_method"),rs.getString("request_path"),rs.getString("result"),rs.getInt("status_code"),rs.getString("change_note"),rs.getTimestamp("occurred_at").toInstant()),id)
                .stream().findFirst().orElseThrow(()->new BusinessException(HttpStatus.NOT_FOUND,"Không tìm thấy nhật ký"));
    }
}
