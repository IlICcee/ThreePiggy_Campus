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

    // ==================== AI 聊天（带 RAG 检索增强 + 个人课表） ====================
    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody ChatRequest request) {
        Map<String, Object> result = new HashMap<>();

        try {
            String userMessage = request.getMessage();
            String userRole = request.getUserRole();
            String userNo = request.getUserNo();
            StringBuilder systemPrompt = new StringBuilder();
            List<String> sources = new ArrayList<>();

            // ── 0. 默认身份提示 ──
            systemPrompt.append("你是 FJUT 校园AI助手。请用中文回答。");
            if (userRole != null && userNo != null) {
                systemPrompt.append(" 当前用户是").append(userRole.equals("teacher") ? "教师" : "学生")
                        .append("，编号").append(userNo).append("。");
            }

            // ── 检测调课意图 ──
            boolean inAdjustFlow = false;
            if ("teacher".equals(userRole)) {
                String msg = userMessage;
                inAdjustFlow = msg.contains("调课") || msg.contains("换课") || msg.contains("调整课程") ||
                    (msg.contains("调") && msg.contains("课"));
                if (!inAdjustFlow && request.getHistory() != null) {
                    for (Map<String, String> h : request.getHistory()) {
                        String c = h.get("content");
                        if (c != null && (c.contains("调课") || c.contains("为您办理调课") || c.contains("已锁定"))) {
                            inAdjustFlow = true; break;
                        }
                    }
                }
            }

            // ── 1. 注入用户个人课表（调课时不注入，避免AI依赖序号） ──
            if (userRole != null && userNo != null && !inAdjustFlow) {
                try {
                    String scheduleText = buildScheduleContext(userRole, userNo);
                    if (!scheduleText.isEmpty()) {
                        systemPrompt.append("\n\n【用户个人课表】\n").append(scheduleText);
                    }
                } catch (Exception e) {
                    log.warn("查询课表失败: {}", e.getMessage());
                }
            }

            // ── 2. RAG: 从向量库检索相关知识 ──
            try {
                SearchRequest searchReq = SearchRequest.builder()
                        .query(userMessage).topK(5).similarityThreshold(0.4).build();
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
                    systemPrompt.append("\n\n【知识库参考内容】\n").append(context)
                            .append("\n\n如果知识库中有相关信息，请基于知识库回答。");
                }
            } catch (Exception e) {
                log.warn("向量检索失败: {}", e.getMessage());
            }

            systemPrompt.append(" 如果以上信息都不足以回答用户问题，请如实说明，不要编造。");

            // ── 0.5 会话历史（最近几轮对话） ──
            if (request.getHistory() != null && !request.getHistory().isEmpty()) {
                StringBuilder hist = new StringBuilder("\n\n【对话历史】\n");
                for (Map<String, String> h : request.getHistory()) {
                    String role = h.get("role");
                    String content = h.get("content");
                    if (content != null && content.length() > 200) content = content.substring(0, 200) + "...";
                    hist.append(role.equals("user") ? "老师：" : "AI：").append(content).append("\n");
                }
                systemPrompt.append(hist);
                systemPrompt.append("请基于以上对话历史理解当前上下文，继续帮老师完成操作。");
            }

            // ── 调课分步引导（使用固定话术模板） ──
            if (inAdjustFlow) {
                systemPrompt.append("\n\n[调课任务-严格使用以下固定话术,每步只做一件事]\n" +
                    "第1步: 用户触发调课 -> 调用listTeacherCourses(teacherNo)获取课程列表。" +
                    "然后必须用此模板回复:\n" +
                    "【固定话术】好的,正在为您办理调课。我查到了您本学期负责的以下课程,请回复对应的编号告诉我您想调整哪一门课:\n" +
                    "(列出每门课,格式:) 编号. 课程名称(班级:XX专业, 时间:周X第X节, 教室:XXX)\n" +
                    "请输入编号:\n\n" +
                    "第2步: 用户输入编号 -> 锁定该课程(记下courseId,原时间,原教室,原班级)。" +
                    "用此模板回复:\n" +
                    "【固定话术】已锁定[课程名称]。请问您想把这节课调整到哪一天?请直接告诉我星期几(例如:周一、周三...)\n\n" +
                    "第3步: 用户输入星期几 -> 调用queryAvailableRooms(courseId, targetDay)获取可用教室。" +
                    "用此模板回复:\n" +
                    "【固定话术】收到,已查询[周X]。根据您所在的院系,目前可供您调课的教室资源如下,请回复对应的编号选择您心仪的时间段和教室:\n" +
                    "(每个选项格式:) 编号. 第X节 | 教室代码(楼名门牌) | 容量X人 | 有/无空调 | 多媒体可用/不可用\n" +
                    "提示:如果当前列出的选项都不合适,您可以回复换一天,我将重新从第二步开始为您匹配。\n\n" +
                    "第4步: 用户输入编号 -> 锁定新时间新教室。用此模板回复:\n" +
                    "【固定话术】已锁定新排期:[新时间]于[新教室]。为了完善调课记录并发送通知,请简要输入本次调课的原因(例如:外出开会、身体不适、教学研讨等):\n\n" +
                    "第5步: 用户输入原因 -> 调用executeAdjustment(courseId, teacherNo, targetDay, targetSlot, targetRoom, reason)。用此模板回复:\n" +
                    "【固定话术】调课成功!课程[课程名称]已正式调整至[周X 第X节][XX教室]。\n" +
                    "通知进度:已向该班级全体学生发布调课公告。如需再次调课,请重新发起调课申请。");
            }

            // ── 调用 AI ──
            String answer = chatClient.prompt()
                    .system(systemPrompt.toString())
                    .user(userMessage)
                    .call()
                    .content();

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

    // ==================== 诊断：单独测 Embedding ====================
    @GetMapping("/knowledge/test-embed")
    public Map<String, Object> testEmbed() {
        Map<String, Object> result = new HashMap<>();
        try {
            String testText = "测试文本：奖学金评定标准";
            org.springframework.ai.document.Document doc = new org.springframework.ai.document.Document(testText, Map.of());
            vectorStore.add(List.of(doc));
            result.put("success", true);
            result.put("message", "嵌入成功！文字: " + testText);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "嵌入失败: " + e.getClass().getSimpleName() + " - " + e.getMessage());
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
    public Map<String, Object> getNotices(@RequestParam(required = false) String major) {
        Map<String, Object> result = new HashMap<>();
        try {
            String sql;
            if (major != null && !major.isEmpty()) {
                sql = "SELECT id, title, content, target_major, publish_time FROM notice " +
                      "WHERE target_major IS NULL OR target_major = ? ORDER BY publish_time DESC";
                result.put("notices", jdbcTemplate.queryForList(sql, major));
            } else {
                sql = "SELECT id, title, content, target_major, publish_time FROM notice ORDER BY publish_time DESC";
                result.put("notices", jdbcTemplate.queryForList(sql));
            }
            result.put("success", true);
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
            String targetMajor = body.getOrDefault("targetMajor", "");
            if (targetMajor.isBlank()) targetMajor = null;
            jdbcTemplate.update(
                "INSERT INTO notice (title, content, target_major, publish_time) VALUES (?, ?, ?, ?)",
                body.get("title"), body.get("content"), targetMajor, LocalDateTime.now()
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
            String targetMajor = body.getOrDefault("targetMajor", "");
            if (targetMajor.isBlank()) targetMajor = null;
            int rows = jdbcTemplate.update(
                "UPDATE notice SET title = ?, content = ?, target_major = ? WHERE id = ?",
                body.get("title"), body.get("content"), targetMajor, id
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

    // ==================== 课表上下文构建 ====================

    private String buildScheduleContext(String role, String no) {
        StringBuilder sb = new StringBuilder();
        if ("teacher".equals(role)) {
            List<Map<String, Object>> courses = jdbcTemplate.queryForList(
                "SELECT c.course_name, c.schedule_day, c.schedule_time, c.start_time, c.classroom, c.major, " +
                "r.has_ac, r.has_multimedia, r.notes " +
                "FROM course c JOIN teacher t ON c.teacher_id = t.id " +
                "LEFT JOIN classroom r ON c.classroom = r.code " +
                "WHERE t.teacher_no = ? ORDER BY c.schedule_day, c.schedule_time", no);
            if (courses.isEmpty()) return "";
            sb.append("授课安排：\n");
            for (Map<String, Object> c : courses) {
                sb.append("- ").append(c.get("schedule_day")).append(" ").append(c.get("schedule_time"))
                  .append("（").append(c.get("start_time")).append("）")
                  .append(" 《").append(c.get("course_name")).append("》")
                  .append(" 班级:").append(c.get("major"))
                  .append(" @").append(c.get("classroom"));
                Boolean ac = (Boolean) c.get("has_ac");
                Boolean mm = (Boolean) c.get("has_multimedia");
                String notes = (String) c.get("notes");
                if (ac != null && !ac) sb.append(" ⚠️无空调");
                if (mm != null && !mm) sb.append(" ⚠️多媒体故障");
                if (notes != null && !notes.isEmpty()) sb.append("（").append(notes).append("）");
                sb.append("\n");
            }
        } else {
            List<Map<String, Object>> info = jdbcTemplate.queryForList(
                "SELECT name, major, class_name FROM student WHERE student_no = ?", no);
            if (info.isEmpty()) return "";
            String major = (String) info.get(0).get("major");
            sb.append("姓名：").append(info.get(0).get("name"))
              .append("，专业：").append(major)
              .append("，班级：").append(info.get(0).get("class_name")).append("\n");

            // -- 课表 --
            List<Map<String, Object>> schedule = jdbcTemplate.queryForList(
                "SELECT c.course_name, t.name AS teacher, c.schedule_day, c.schedule_time, c.start_time, " +
                "c.classroom, c.credits, r.has_ac, r.has_multimedia, r.notes " +
                "FROM course c LEFT JOIN teacher t ON c.teacher_id = t.id " +
                "LEFT JOIN classroom r ON c.classroom = r.code " +
                "WHERE c.major = ? ORDER BY c.schedule_day, c.schedule_time", major);
            if (!schedule.isEmpty()) {
                sb.append("课表：\n");
                for (Map<String, Object> c : schedule) {
                    sb.append("- ").append(c.get("schedule_day")).append(" ").append(c.get("schedule_time"))
                      .append("（").append(c.get("start_time")).append("）")
                      .append(" 《").append(c.get("course_name")).append("》")
                      .append(" @").append(c.get("classroom"))
                      .append("（").append(c.get("teacher")).append("老师）");
                    Boolean ac = (Boolean) c.get("has_ac");
                    Boolean mm = (Boolean) c.get("has_multimedia");
                    if (ac != null && !ac) sb.append(" ⚠️无空调");
                    if (mm != null && !mm) sb.append(" ⚠️多媒体故障");
                    sb.append("\n");
                }
            }

            // -- 成绩 --
            List<Map<String, Object>> grades = jdbcTemplate.queryForList(
                "SELECT c.course_name, g.score, g.grade_point, c.credits " +
                "FROM grade g JOIN course c ON g.course_id = c.id " +
                "JOIN student s ON g.student_id = s.id WHERE s.student_no = ?", no);
            if (!grades.isEmpty()) {
                double total = 0; double weighted = 0;
                sb.append("成绩：\n");
                for (Map<String, Object> g : grades) {
                    sb.append("- ").append(g.get("course_name")).append("：").append(g.get("score")).append("分\n");
                    double gp = ((Number) g.get("grade_point")).doubleValue();
                    double cr = ((Number) g.get("credits")).doubleValue();
                    weighted += gp * cr; total += cr;
                }
                double gpa = total > 0 ? Math.round(weighted / total * 100.0) / 100.0 : 0;
                sb.append("GPA：").append(gpa).append("\n");
            }
        }
        return sb.toString();
    }

    public static class ChatRequest {
        private String message;
        private String userRole;
        private String userNo;
        private List<Map<String, String>> history;  // [{role:"user"/"ai", content:"..."}]
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getUserRole() { return userRole; }
        public void setUserRole(String userRole) { this.userRole = userRole; }
        public String getUserNo() { return userNo; }
        public void setUserNo(String userNo) { this.userNo = userNo; }
        public List<Map<String, String>> getHistory() { return history; }
        public void setHistory(List<Map<String, String>> history) { this.history = history; }
    }
}
