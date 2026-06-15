package com.example.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatClient chatClient;
    private final JdbcTemplate jdbcTemplate;

    public ChatController(ChatClient chatClient, JdbcTemplate jdbcTemplate) {
        this.chatClient = chatClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ==================== AI 聊天 ====================
    @PostMapping("/chat")
    public String chat(@RequestBody ChatRequest request) {
        return chatClient.prompt()
                .user(request.getMessage())
                .call()
                .content();
    }

    // ==================== 公告查询 ====================
    @GetMapping("/notices")
    public Map<String, Object> getNotices() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> notices = jdbcTemplate.queryForList(
                "SELECT id, title, content, publish_time FROM notice ORDER BY publish_time DESC"
            );
            result.put("success", true);
            result.put("notices", notices);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "公告查询失败: " + e.getMessage());
        }
        return result;
    }

    // ==================== 公告管理（教师） ====================
    @PostMapping("/notices")
    public Map<String, Object> createNotice(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            jdbcTemplate.update(
                "INSERT INTO notice (title, content, publish_time) VALUES (?, ?, ?)",
                body.get("title"), body.get("content"), LocalDateTime.now()
            );
            result.put("success", true);
            result.put("message", "公告发布成功");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "发布失败: " + e.getMessage());
        }
        return result;
    }

    @PutMapping("/notices/{id}")
    public Map<String, Object> updateNotice(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            int rows = jdbcTemplate.update(
                "UPDATE notice SET title = ?, content = ? WHERE id = ?",
                body.get("title"), body.get("content"), id
            );
            result.put("success", rows > 0);
            result.put("message", rows > 0 ? "公告更新成功" : "公告不存在");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "更新失败: " + e.getMessage());
        }
        return result;
    }

    @DeleteMapping("/notices/{id}")
    public Map<String, Object> deleteNotice(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            int rows = jdbcTemplate.update("DELETE FROM notice WHERE id = ?", id);
            result.put("success", rows > 0);
            result.put("message", rows > 0 ? "公告已删除" : "公告不存在");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "删除失败: " + e.getMessage());
        }
        return result;
    }

    // ==================== 聊天会话管理 ====================
    @GetMapping("/chat/sessions")
    public Map<String, Object> getSessions(@RequestParam Long userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> sessions = jdbcTemplate.queryForList(
                "SELECT id, title, created_at, updated_at FROM chat_session WHERE user_id = ? ORDER BY updated_at DESC",
                userId
            );
            result.put("success", true);
            result.put("sessions", sessions);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "查询失败: " + e.getMessage());
        }
        return result;
    }

    @PostMapping("/chat/sessions")
    public Map<String, Object> createSession(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long userId = Long.valueOf(body.get("userId").toString());
            String title = (String) body.getOrDefault("title", "新对话");
            jdbcTemplate.update(
                "INSERT INTO chat_session (user_id, title, created_at, updated_at) VALUES (?, ?, ?, ?)",
                userId, title, LocalDateTime.now(), LocalDateTime.now()
            );
            // 获取刚创建的 session id (H2 兼容: 用 TOP 1 而非 LIMIT)
            List<Map<String, Object>> sessions = jdbcTemplate.queryForList(
                "SELECT TOP 1 id, title, created_at, updated_at FROM chat_session WHERE user_id = ? ORDER BY id DESC",
                userId
            );
            Map<String, Object> session = sessions.isEmpty() ? null : sessions.get(0);
            if (session == null) throw new RuntimeException("会话创建失败");
            result.put("success", true);
            result.put("session", session);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "创建失败: " + e.getMessage());
        }
        return result;
    }

    @GetMapping("/chat/sessions/{id}/messages")
    public Map<String, Object> getMessages(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> messages = jdbcTemplate.queryForList(
                "SELECT id, role, content, created_at FROM chat_message WHERE session_id = ? ORDER BY created_at ASC",
                id
            );
            result.put("success", true);
            result.put("messages", messages);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "查询失败: " + e.getMessage());
        }
        return result;
    }

    @PostMapping("/chat/sessions/{id}/messages")
    public Map<String, Object> saveMessage(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            String role = (String) body.get("role");
            String content = (String) body.get("content");
            jdbcTemplate.update(
                "INSERT INTO chat_message (session_id, role, content, created_at) VALUES (?, ?, ?, ?)",
                id, role, content, LocalDateTime.now()
            );
            // 更新 session 的 updated_at
            jdbcTemplate.update("UPDATE chat_session SET updated_at = ? WHERE id = ?", LocalDateTime.now(), id);
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "保存失败: " + e.getMessage());
        }
        return result;
    }

    @DeleteMapping("/chat/sessions/{id}")
    public Map<String, Object> deleteSession(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            jdbcTemplate.update("DELETE FROM chat_message WHERE session_id = ?", id);
            jdbcTemplate.update("DELETE FROM chat_session WHERE id = ?", id);
            result.put("success", true);
            result.put("message", "会话已删除");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "删除失败: " + e.getMessage());
        }
        return result;
    }

    public static class ChatRequest {
        private String message;
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
