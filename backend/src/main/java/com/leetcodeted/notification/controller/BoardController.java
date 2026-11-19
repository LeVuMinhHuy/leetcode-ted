package com.leetcodeted.notification.controller;

import com.leetcodeted.notification.service.BoardService;
import com.leetcodeted.notification.service.BoardService.MemberSnapshotDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    @GetMapping("/board")
    public Map<String, List<MemberSnapshotDto>> getBoard() {
        return Map.of("members", boardService.getBoard());
    }

    @GetMapping("/members")
    public Map<String, List<MemberSnapshotDto>> listMembers() {
        return Map.of("members", boardService.getBoard());
    }

    @PostMapping("/members")
    public ResponseEntity<?> joinBoard(@RequestBody JoinRequest body) {
        String username = body.username() != null ? body.username().trim() : "";
        if (username.length() < 3 || username.length() > 30 || !username.matches("[a-zA-Z0-9_-]+")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Use a valid LeetCode username (3-30 letters, numbers, _ or -)."));
        }
        try {
            var member = boardService.joinBoard(username, body.displayName());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("member", Map.of("username", member.getUsername(),
                            "displayName", member.getDisplayName() != null ? member.getDisplayName() : member.getUsername())));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "That LeetCode username was not found."));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to join the board"));
        }
    }

    @GetMapping("/members/{username}/heatmap")
    public ResponseEntity<?> getHeatmap(@PathVariable String username,
                                        @RequestParam(required = false) Integer year) {
        if (year == null) year = java.time.Year.now().getValue();
        return boardService.getYearHeatmap(username.trim().toLowerCase(), year)
                .map(h -> ResponseEntity.ok((Object) h))
                .orElse(ResponseEntity.notFound().build());
    }

    record JoinRequest(String username, String displayName) {}
}
