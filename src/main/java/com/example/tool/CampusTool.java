package com.example.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class CampusTool {

    private static final Logger log = LoggerFactory.getLogger(CampusTool.class);

    private final JdbcTemplate jdbcTemplate;

    public CampusTool(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Tool(description = "查询最近的校园公告，返回公告标题和内容列表")
    public String getRecentNotices() {
        try {
            String sql = "SELECT title, content FROM notice ORDER BY publish_time DESC LIMIT 5";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            if (rows.isEmpty()) {
                return "暂无公告";
            }
            return rows.stream()
                    .map(row -> "【" + row.get("title") + "】" + row.get("content"))
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.error("查询公告失败", e);
            return "公告查询失败: " + e.getMessage();
        }
    }

    // ==================== 调课工具（简洁分步版） ====================

    /**
     * 【第1步】列出教师所有课程（Step 1固定格式）
     */
    @Tool(description = "列出教师所有课程。返回每门课的ID,名称,专业,时间,教室,用于Step1模板")
    public String listTeacherCourses(
            @ToolParam(description = "教师编号") String teacherNo) {
        try {
            List<Map<String, Object>> tea = jdbcTemplate.queryForList(
                "SELECT id, name FROM teacher WHERE teacher_no = ?", teacherNo);
            if (tea.isEmpty()) return "教师不存在";
            Long tid = ((Number) tea.get(0).get("id")).longValue();
            String tname = (String) tea.get(0).get("name");

            List<Map<String, Object>> courses = jdbcTemplate.queryForList(
                "SELECT c.id, c.course_name, c.major, c.schedule_day, c.schedule_time, " +
                "c.start_time, c.classroom, cl.building " +
                "FROM course c LEFT JOIN classroom cl ON c.classroom = cl.code " +
                "WHERE c.teacher_id = ? ORDER BY c.major, c.schedule_day, c.schedule_time", tid);

            if (courses.isEmpty()) return tname + "老师暂无授课安排";

            StringBuilder sb = new StringBuilder();
            sb.append(tname).append("老师的课程:\n");
            for (int i = 0; i < courses.size(); i++) {
                Map<String, Object> c = courses.get(i);
                sb.append(i + 1).append(". [课程ID=").append(c.get("id")).append("] ")
                  .append(c.get("course_name")).append("(")
                  .append("班级:").append(c.get("major")).append(", ")
                  .append("时间:").append(c.get("schedule_day")).append(" ").append(c.get("schedule_time"))
                  .append("(").append(c.get("start_time")).append("), ")
                  .append("教室:").append(c.get("classroom")).append("(").append(c.get("building")).append(")")
                  .append(")\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("查询失败", e);
            return "查询失败: " + e.getMessage();
        }
    }

    /**
     * 【第3步】查询目标天的可用教室（Step 3 编号格式）
     */
    @Tool(description = "查询目标天可用时段教室,返回编号列表格式,含AC/多媒体状态。自动合班检测")
    public String queryAvailableRooms(
            @ToolParam(description = "课程ID") Long courseId,
            @ToolParam(description = "目标星期几") String targetDay) {
        try {
            List<Map<String, Object>> ci = jdbcTemplate.queryForList(
                "SELECT c.course_name, c.major, c.schedule_time AS orig_slot, c.teacher_id " +
                "FROM course c WHERE c.id = ?", courseId);
            if (ci.isEmpty()) return "课程不存在";
            String courseName = (String) ci.get(0).get("course_name");
            String major = (String) ci.get(0).get("major");
            String origSlot = (String) ci.get(0).get("orig_slot");
            Long teacherId = ((Number) ci.get(0).get("teacher_id")).longValue();

            // 合班检测
            List<String> allMajors = jdbcTemplate.queryForList(
                "SELECT DISTINCT major FROM course WHERE teacher_id = ? AND course_name = ?",
                String.class, teacherId, courseName);
            List<String> allowedBlds = getAllowedBuildings(major);
            String[] slots = {"1-2节", "3-4节", "5-6节", "7-8节"};

            // 收集所有可用选项
            List<String> options = new ArrayList<>();
            int num = 1;

            for (String bld : allowedBlds) {
                for (String slot : slots) {
                    if (slot.equals(origSlot)) continue;
                    // 合班冲突检测
                    boolean blocked = false;
                    for (String m : allMajors) {
                        int cnt = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM course WHERE major=? AND schedule_day=? AND schedule_time=? AND id!=?",
                            Integer.class, m, targetDay, slot, courseId);
                        if (cnt > 0) { blocked = true; break; }
                    }
                    if (blocked) continue;

                    // 查可用教室
                    List<Map<String, Object>> rooms = jdbcTemplate.queryForList(
                        "SELECT r.code, r.room_no, r.capacity, r.has_ac, r.has_multimedia, r.notes FROM classroom r " +
                        "WHERE r.building=? AND r.code NOT IN " +
                        "(SELECT DISTINCT classroom FROM course WHERE schedule_day=? AND schedule_time=? AND classroom IS NOT NULL) " +
                        "ORDER BY r.room_no",
                        bld, targetDay, slot);
                    for (Map<String, Object> r : rooms) {
                        String ac = Boolean.TRUE.equals(r.get("has_ac")) ? "有空调" : "无空调";
                        String mm = Boolean.TRUE.equals(r.get("has_multimedia")) ? "多媒体可用" : "多媒体不可用";
                        String notes = (String) r.get("notes");
                        if (notes != null && !notes.isEmpty()) mm += "(" + notes + ")";
                        options.add(num + ". " + slot + " | " + r.get("code") + "(" + bld + r.get("room_no") + ")" +
                                   " | 容量" + r.get("capacity") + "人 | " + ac + " | " + mm);
                        num++;
                    }
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append(targetDay).append("可用教室(").append(major).append("专业,可选楼:");
            sb.append(String.join("/", allowedBlds)).append("):\n");
            if (allMajors.size() > 1) {
                sb.append("(合班:").append(String.join(",", allMajors)).append(",以下为共同空闲)\n");
            }
            if (options.isEmpty()) {
                sb.append(targetDay).append("当天您院系管辖范围内的教室已全部排满,无法调课。建议您换一天试试。");
            } else {
                for (String opt : options) {
                    sb.append(opt).append("\n");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("查询失败", e);
            return "查询失败: " + e.getMessage();
        }
    }

    /**
     * 【第5步】执行调课
     */
    @Tool(description = "执行调课。成功后自动更新课表并向相关专业学生发通知。")
    public String executeAdjustment(
            @ToolParam(description = "课程ID") Long courseId,
            @ToolParam(description = "教师编号") String teacherNo,
            @ToolParam(description = "目标星期几") String targetDay,
            @ToolParam(description = "目标节次") String targetSlot,
            @ToolParam(description = "目标教室") String targetRoom,
            @ToolParam(description = "调课原因") String reason) {
        try {
            List<Map<String, Object>> ci = jdbcTemplate.queryForList(
                "SELECT c.*, t.name AS tname FROM course c JOIN teacher t ON c.teacher_id = t.id " +
                "WHERE c.id = ? AND t.teacher_no = ?", courseId, teacherNo);
            if (ci.isEmpty()) return "课程不存在或不属于当前教师";

            Map<String, Object> c = ci.get(0);
            String origDay = (String) c.get("schedule_day");
            String origSlot = (String) c.get("schedule_time");
            String origRoom = (String) c.get("classroom");
            String cname = (String) c.get("course_name");
            String major = (String) c.get("major");
            String tname = (String) c.get("tname");

            // 检查冲突
            int conflict = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM course WHERE schedule_day=? AND schedule_time=? AND classroom=? AND id!=?",
                Integer.class, targetDay, targetSlot, targetRoom, courseId);
            if (conflict > 0) return "❌ " + targetRoom + " 在 " + targetDay + " " + targetSlot + " 已被占用，请重新选择。";

            // 建筑规则
            String bld = jdbcTemplate.queryForObject(
                "SELECT building FROM classroom WHERE code = ?", String.class, targetRoom);
            if (bld == null) return "❌ 教室 " + targetRoom + " 不存在。";
            if (!getAllowedBuildings(major).contains(bld)) {
                return "❌ " + major + "专业不能使用" + bld + "，仅限" + String.join("、", getAllowedBuildings(major));
            }

            // 更新
            jdbcTemplate.update(
                "UPDATE course SET schedule_day=?, schedule_time=?, classroom=? WHERE id=?",
                targetDay, targetSlot, targetRoom, courseId);

            // 合班通知
            List<String> affected = jdbcTemplate.queryForList(
                "SELECT DISTINCT major FROM course WHERE teacher_id=(SELECT teacher_id FROM course WHERE id=?) AND course_name=?",
                String.class, courseId, cname);
            if (affected.isEmpty()) affected = List.of(major);
            String rsn = (reason != null && !reason.isEmpty()) ? reason : "教学安排调整";
            for (String m : affected) {
                jdbcTemplate.update(
                    "INSERT INTO notice (title, content, target_major, publish_time) VALUES (?,?,?,?)",
                    "📢 调课通知",
                    String.format("《%s》%s老师 | %s %s @%s → %s %s @%s | 原因：%s | 请%s同学查最新课表",
                        cname, tname, origDay, origSlot, origRoom, targetDay, targetSlot, targetRoom, rsn, m),
                    m, LocalDateTime.now());
            }

            return "调课成功! 课程[" + cname + "]已正式调整至[" + targetDay + " " + targetSlot + "][" + targetRoom + "]。\n" +
                   "通知进度: 已向" + String.join("、", affected) + "专业全体学生发布调课公告。\n" +
                   "如需再次调课, 请重新发起调课申请。";
        } catch (Exception e) {
            log.error("调课失败", e);
            return "调课失败: " + e.getMessage();
        }
    }

    // ==================== 辅助方法 ====================

    private List<String> getAllowedBuildings(String major) {
        if ("物联网工程".equals(major) || "计算机科学".equals(major)) {
            return List.of("至真楼", "博学楼");
        } else if ("法学".equals(major) || "金融".equals(major)) {
            return List.of("明法楼", "博学楼");
        }
        // 默认允许所有教学楼
        return List.of("至真楼", "明法楼", "博学楼");
    }
}