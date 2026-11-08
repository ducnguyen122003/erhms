package com.erhms.workspace;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.security.access.AccessDeniedException;
import com.erhms.role.DashboardRole;
import com.erhms.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;
import java.io.IOException;
import java.util.*;

@Component
public class ScreenCatalog {
    public record Screen(String code,String fe,String title,String kind,String functionCode,List<String> roles,boolean publicScreen) {}
    private final Map<String,Screen> screens=new LinkedHashMap<>();
    public ScreenCatalog(ObjectMapper mapper) throws IOException {
        try(var input=new ClassPathResource("workspace/screen-catalog.json").getInputStream()) {
            List<Map<String,Object>> items=mapper.readValue(input,new TypeReference<>(){});
            for(var item:items) {
                @SuppressWarnings("unchecked") var roles=(List<String>)item.get("roles");
                String code=(String)item.get("code");
                screens.put(code,new Screen(code,(String)item.get("fe"),(String)item.get("title"),(String)item.get("kind"),
                        (String)item.get("functionCode"),roles,Boolean.TRUE.equals(item.get("public"))));
            }
        }
    }
    public Screen get(String code) {
        var screen=screens.get(code);
        if(screen==null)throw new BusinessException(HttpStatus.NOT_FOUND,"Không tìm thấy màn hình");
        return screen;
    }
    public Optional<Screen> find(String code) { return Optional.ofNullable(screens.get(code)); }
    public List<Long> allowedRoles(String code) {
        return find(code).map(s->s.roles().stream().map(r->Arrays.stream(DashboardRole.values()).filter(d->d.name().equals(r)).findFirst().orElseThrow().id()).toList()).orElse(List.of());
    }
    public void requireRole(Screen screen,String role) {
        if(!screen.roles().contains(role))throw new AccessDeniedException("Vai trò không được mở màn này theo ma trận quyền");
    }
}
