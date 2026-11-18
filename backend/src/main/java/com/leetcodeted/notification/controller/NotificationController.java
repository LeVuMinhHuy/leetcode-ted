package com.leetcodeted.notification.controller;

import com.leetcodeted.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/member-solved")
    public ResponseEntity<?> memberSolved(@RequestBody MemberSolvedRequest request) {
        if (request == null || request.solver() == null
                || request.solver().username() == null || request.solver().username().isBlank()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("solver.username is required"));
        }
        if (request.problemId() == null || request.problemId() <= 0) {
            return ResponseEntity.badRequest().body(new ErrorResponse("problemId must be a positive integer"));
        }

        SolverDto solver = request.solver();
        log.debug("Received member-solved notification: solver='{}', problemId={}",
                solver.username(), request.problemId());
        notificationService.publishMemberSolved(solver.username(), solver.displayName(), request.problemId());

        return ResponseEntity.ok(new StatusResponse("ok"));
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(new StatusResponse("ok"));
    }

    private record SolverDto(String username, String displayName) {}

    private record MemberSolvedRequest(SolverDto solver, Integer problemId) {}

    private record StatusResponse(String status) {}

    private record ErrorResponse(String error) {}
}
