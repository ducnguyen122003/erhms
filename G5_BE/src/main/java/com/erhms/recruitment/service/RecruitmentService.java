package com.erhms.recruitment.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.erhms.recruitment.dto.RequestDtos.Command;
import com.erhms.recruitment.dto.RequestDtos.EventView;
import com.erhms.recruitment.dto.RequestDtos.Input;
import com.erhms.recruitment.dto.RequestDtos.PageResult;
import com.erhms.recruitment.dto.RequestDtos.View;
import com.erhms.recruitment.entity.RecruitmentRequest;
import com.erhms.recruitment.entity.RequestEvent;
import com.erhms.recruitment.enums.RequestStatus;
import com.erhms.recruitment.repository.RecruitmentRepository;
import com.erhms.recruitment.repository.RequestEventRepository;
import com.erhms.role.entity.Role;
import com.erhms.shared.exception.BusinessException;
import com.erhms.user.entity.User;
import com.erhms.user.repository.UserRepository;
import com.erhms.user.service.CurrentUser;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RecruitmentService {

    private final RecruitmentRepository repo;
    private final RequestEventRepository events;
    private final CurrentUser current;
    private final UserRepository users;

    public RecruitmentService(
            RecruitmentRepository repo,
            RequestEventRepository events,
            CurrentUser current,
            UserRepository users) {
        this.repo = repo;
        this.events = events;
        this.current = current;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public PageResult list(String q, RequestStatus status, int page, int size) {
        var user = current.get();
        if (!isManager(user)) {
            requireUser(user);
        }
        var result = repo.search(
                isManager(user) ? null : user.id,
                status,
                q,
                PageRequest.of(
                        Math.max(page, 0),
                        Math.max(1, Math.min(size, 50)),
                        Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"))));
        return new PageResult(
                result.getContent().stream().map(this::view).toList(),
                result.getTotalElements(),
                result.getNumber(),
                result.getSize(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public View get(UUID id) {
        var request = find(id, false);
        access(request, current.get());
        return view(request);
    }

    @Transactional(readOnly = true)
    public List<EventView> history(UUID id) {
        var request = find(id, false);
        access(request, current.get());
        return events.findByRequestIdOrderByOccurredAtAsc(id).stream()
                .map(event -> new EventView(event.action, event.note, event.occurredAt))
                .toList();
    }

    public View create(Input input) {
        var user = current.get();
        requireUser(user);
        validateDate(input.neededBy());

        var request = new RecruitmentRequest();
        request.id = UUID.randomUUID();
        request.createdBy = user.id;
        request.status = RequestStatus.DRAFT;
        request.createdAt = Instant.now();
        apply(request, input);
        repo.saveAndFlush(request);
        event(request, user, "CREATED", null);
        return view(request);
    }

    public View update(UUID id, Input input) {
        var user = current.get();
        requireUser(user);
        var request = find(id, true);
        access(request, user);
        version(request, input.version());
        editable(request);
        validateDate(input.neededBy());

        apply(request, input);
        repo.flush();
        event(request, user, "UPDATED", null);
        return view(request);
    }

    public View command(UUID id, String action, Command command) {
        var user = current.get();
        var request = find(id, true);
        access(request, user);
        version(request, command.version());

        switch (action) {
            case "submit" -> {
                requireUser(user);
                editable(request);
                validateDate(request.neededBy);
                request.status = RequestStatus.SUBMITTED;
                request.reviewNote = null;
            }
            case "approve", "request-revision" -> {
                requireManager(user);
                if (Objects.equals(request.createdBy, user.id)) {
                    throw new BusinessException(FORBIDDEN, "Không được tự duyệt yêu cầu do mình tạo");
                }
                state(request, RequestStatus.SUBMITTED);
                if (action.equals("approve")) {
                    validateDate(request.neededBy);
                    request.status = RequestStatus.APPROVED;
                    request.reviewNote = null;
                } else {
                    if (command.note() == null || command.note().isBlank()) {
                        throw new BusinessException(BAD_REQUEST, "Nhập lý do yêu cầu chỉnh sửa");
                    }
                    request.status = RequestStatus.REVISION_REQUIRED;
                    request.reviewNote = command.note().strip();
                }
            }
            case "close" -> {
                requireManager(user);
                state(request, RequestStatus.APPROVED);
                request.status = RequestStatus.CLOSED;
            }
            default -> throw new BusinessException(NOT_FOUND, "Hành động không tồn tại");
        }

        request.updatedAt = Instant.now();
        repo.flush();
        event(request, user, action.toUpperCase(Locale.ROOT), command.note());
        return view(request);
    }

    private void apply(RecruitmentRequest request, Input input) {
        request.title = input.title().strip();
        request.quantity = input.quantity();
        request.shift = input.shift();
        request.skills = input.skills().strip();
        request.location = input.location().strip();
        request.neededBy = input.neededBy();
        request.updatedAt = Instant.now();
    }

    private void validateDate(LocalDate date) {
        if (date == null || date.isBefore(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")))) {
            throw new BusinessException(BAD_REQUEST, "Ngày cần nhân sự không được ở quá khứ");
        }
    }

    private void editable(RecruitmentRequest request) {
        if (request.status != RequestStatus.DRAFT && request.status != RequestStatus.REVISION_REQUIRED) {
            throw new BusinessException(CONFLICT, "Chỉ được sửa hoặc gửi bản nháp và yêu cầu cần chỉnh sửa");
        }
    }

    private void state(RecruitmentRequest request, RequestStatus expected) {
        if (request.status != expected) {
            throw new BusinessException(CONFLICT, "Trạng thái hiện tại không cho phép thao tác này");
        }
    }

    private void version(RecruitmentRequest request, long version) {
        if (request.version != version) {
            throw new BusinessException(CONFLICT, "Dữ liệu đã thay đổi. Hãy đóng và mở lại bản ghi.");
        }
    }

    private boolean isManager(User user) {
        return user.hasRole(Role.SERVICE_MANAGER);
    }

    private void requireManager(User user) {
        if (!isManager(user)) {
            throw new AccessDeniedException("Chỉ quản lý được thực hiện");
        }
    }

    private void requireUser(User user) {
        if (!user.hasRole(Role.USER)) {
            throw new AccessDeniedException("Chỉ người dùng được thực hiện");
        }
    }

    private void access(RecruitmentRequest request, User user) {
        if (!isManager(user)
                && (!user.hasRole(Role.USER) || !Objects.equals(request.createdBy, user.id))) {
            throw new BusinessException(NOT_FOUND, "Không tìm thấy yêu cầu");
        }
    }

    private RecruitmentRequest find(UUID id, boolean lock) {
        return (lock ? repo.lockById(id) : repo.findById(id))
                .orElseThrow(() -> new BusinessException(NOT_FOUND, "Không tìm thấy yêu cầu"));
    }

    private void event(RecruitmentRequest request, User user, String action, String note) {
        var event = new RequestEvent();
        event.id = UUID.randomUUID();
        event.requestId = request.id;
        event.actorId = user.id;
        event.action = action;
        event.note = note;
        event.occurredAt = Instant.now();
        events.save(event);
    }

    private View view(RecruitmentRequest request) {
        var user = users.findById(request.createdBy)
                .orElseThrow(() -> new BusinessException(NOT_FOUND, "Không tìm thấy người tạo yêu cầu"));
        return new View(
                request.id,
                request.createdBy,
                user.fullName,
                request.title,
                request.quantity,
                request.shift,
                request.skills,
                request.location,
                request.neededBy,
                request.status,
                request.reviewNote,
                request.createdAt,
                request.updatedAt,
                request.version);
    }
}
