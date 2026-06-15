package com.example.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import com.example.service.DocumentService;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatClient chatClient;
    private final JdbcTemplate jdbcTemplate;
    private final PgVectorStore vectorStore;
    private final DocumentService documentService;

    public ChatController(ChatClient chatClient,
                          JdbcTemplate jdbcTemplate,
                          PgVectorStore vectorStore,
                          DocumentService documentService) {
        this.chatClient = chatClient;
        this.jdbcTemplate = jdbcTemplate;
        this.vectorStore = vectorStore;
        this.documentService = documentService;
    }

    // ==================== AI 聊天（带 RAG 检索增强） ====================
    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody ChatRequest request) {
        Map<String, Object> result = new HashMap<>();

        try {
            String userMessage = request.getMessage();
            String systemPrompt = null;
            List<String> sources = new ArrayList<>();

            // ── RAG: 从向量库检索相关知识 ──
            try {
                SearchRequest searchReq = SearchRequest.builder()
                        .query(userMessage)
                        .topK(5)
                        .similarityThreshold(0.4)
                        .build();
                List<Document> docs = vectorStore.similaritySearch(searchReq);
                if (!docs.isEmpty()) {
                    String context = docs.stream()
                            .map(d -> {
                                String src = d.getMetadata() != null
                                        ? (String) d.getMetadata().getOrDefault("source", "未知")
                                        : "未知";
                                sources.add(src);
                                return d.getText();
                            })
                            .collect(Collectors.joining("\n\n---\n\n"));

                    systemPrompt = """
                            你是 FJUT 校园AI助手。请**仅基于**以下知识库内容回答用户问题。
                            如果知识库中没有相关信息，请如实说"知识库中暂无相关信息"，
                            不要自行编造。

                            【知识库参考内容】
                            %s
                            """.formatted(context);
                }
            } catch (Exception e) {
                log.warn("向量检索失败（可能向量库为空）: {}", e.getMessage());
            }

            // ── 调用 AI ──
            String answer;
            if (systemPrompt != null) {
                answer = chatClient.prompt()
                        .system(systemPrompt)
                        .user(userMessage)
                        .call()
                        .content();
            } else {
                answer = chatClient.prompt()
                        .user(userMessage)
                        .call()
                        .content();
            }

            result.put("success", true);
            result.put("message", answer);
            if (!sources.isEmpty()) {
                result.put("sources", sources.stream().distinct().toList());
            }

        } catch (Exception e) {
            log.error("AI 对话失败", e);
            result.put("success", false);
            result.put("message", "AI 服务异常: " + e.getMessage());
        }

        return result;
    }

    // ==================== 知识库导入 ====================
    @PostMapping("/knowledge/import")
    public Map<String, Object> importKnowledge() {
        log.info("开始导入知识库文档...");
        return documentService.importAllDocuments();
    }

    // ==================== 知识库查询（测试用） ====================
    @GetMapping("/knowledge/search")
    public Map<String, Object> searchKnowledge(@RequestParam String q) {
        Map<String, Object> result = new HashMap<>();
        try {
            SearchRequest searchReq = SearchRequest.builder()
                    .query(q)
                    .topK(5)
                    .similarityThreshold(0.5)
                    .build();
            List<Document> docs = vectorStore.similaritySearch(searchReq);
            List<Map<String, String>> items = docs.stream()
                    .map(d -> {
                        Map<String, String> m = new LinkedHashMap<>();
                        String text = d.getText();
                        m.put("content", text.length() > 200
                                ? text.substring(0, 200) + "..."
                                : text);
                        m.put("source", d.getMetadata() != null
                                ? (String) d.getMetadata().getOrDefault("source", "")
                                : "");
                        return m;
                    })
                    .toList();
            result.put("success", true);
            result.put("count", items.size());
            result.put("results", items);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "检索失败: " + e.getMessage());
        }
        return result;
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
            List<Map<String, Object>> sessions = jdbcTemplate.queryForList(
                "SELECT id, title, created_at, updated_at FROM chat_session WHERE user_id = ? ORDER BY id DESC LIMIT 1",
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
