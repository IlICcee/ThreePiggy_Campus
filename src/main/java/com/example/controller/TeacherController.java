package com.example.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/teacher")
public class TeacherController {

    private static final Logger log = LoggerFactory.getLogger(TeacherController.class);
    private final JdbcTemplate jdbc;

    public TeacherController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ==================== 教师授课专业 ====================
    @GetMapping("/majors")
    public Map<String, Object> majors(@RequestParam String teacherNo) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> list = jdbc.queryForList(
                "SELECT DISTINCT c.major FROM course c " +
                "WHERE c.teacher_id = (SELECT id FROM teacher WHERE teacher_no = ?) " +
                "ORDER BY c.major", teacherNo);
            result.put("success", true);
            result.put("majors", list.stream().map(m -> m.get("major")).toList());
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "查询失败: " + e.getMessage());
        }
        return result;
    }

    // ==================== 教师课表（增强版：含授课班级） ====================
    @GetMapping("/schedule")
    public Map<String, Object> schedule(@RequestParam String teacherNo) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> teaList = jdbc.queryForList(
                "SELECT id, name FROM teacher WHERE teacher_no = ?", teacherNo);
            if (teaList.isEmpty()) {
                result.put("success", false); result.put("message", "教师不存在"); return result;
            }
            Long tid = ((Number) teaList.get(0).get("id")).longValue();

            List<Map<String, Object>> courses = jdbc.queryForList(
                "SELECT c.id, c.course_code, c.course_name, c.major, c.schedule_day, c.schedule_time, " +
                "c.start_time, c.classroom, r.has_ac, r.has_multimedia, r.notes " +
                "FROM course c LEFT JOIN classroom r ON c.classroom = r.code " +
                "WHERE c.teacher_id = ? ORDER BY c.schedule_day, c.schedule_time", tid);

            result.put("success", true);
            result.put("name", teaList.get(0).get("name"));
            result.put("courses", courses);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "查询失败: " + e.getMessage());
        }
        return result;
    }

    // ==================== 教师可调课程列表 ====================
    @GetMapping("/courses-for-adjustment")
    public Map<String, Object> coursesForAdjustment(@RequestParam String teacherNo) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> teaList = jdbc.queryForList(
                "SELECT id, name FROM teacher WHERE teacher_no = ?", teacherNo);
            if (teaList.isEmpty()) {
                result.put("success", false); result.put("message", "教师不存在"); return result;
            }
            Long tid = ((Number) teaList.get(0).get("id")).longValue();

            List<Map<String, Object>> courses = jdbc.queryForList(
                "SELECT c.id, c.course_code, c.course_name, c.major, c.schedule_day, " +
                "c.schedule_time, c.start_time, c.classroom, cl.building, " +
                "cl.has_ac, cl.has_multimedia, cl.notes " +
                "FROM course c " +
                "LEFT JOIN classroom cl ON c.classroom = cl.code " +
                "WHERE c.teacher_id = ? " +
                "ORDER BY c.major, c.schedule_day, c.schedule_time", tid);

            result.put("success", true);
            result.put("name", teaList.get(0).get("name"));
            result.put("courses", courses);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "查询失败: " + e.getMessage());
        }
        return result;
    }

    // ==================== 查询可调课的教室 ====================
    @GetMapping("/available-rooms")
    public Map<String, Object> availableRooms(
            @RequestParam String major,
            @RequestParam String targetDay,
            @RequestParam String targetSlot,
            @RequestParam(required = false) Long courseId) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<String> allowedBuildings = getAllowedBuildings(major);

            // 检测合班专业（如果传了courseId）
            List<String> allMajors = List.of(major);
            if (courseId != null) {
                List<Map<String, Object>> ci = jdbc.queryForList(
                    "SELECT course_name, teacher_id FROM course WHERE id = ?", courseId);
                if (!ci.isEmpty()) {
                    String cn = (String) ci.get(0).get("course_name");
                    Long tid = ((Number) ci.get(0).get("teacher_id")).longValue();
                    allMajors = jdbc.queryForList(
                        "SELECT DISTINCT major FROM course WHERE teacher_id = ? AND course_name = ?",
                        String.class, tid, cn);
                }
            }

            // 检查所有相关专业在目标时段是否空闲
            boolean majorConflict = false;
            for (String m : allMajors) {
                List<Map<String, Object>> conflicts = jdbc.queryForList(
                    "SELECT id FROM course WHERE major = ? AND schedule_day = ? AND schedule_time = ?",
                    m, targetDay, targetSlot);
                if (courseId != null) {
                    conflicts = conflicts.stream()
                        .filter(c -> !c.get("id").equals(courseId)).toList();
                }
                if (!conflicts.isEmpty()) { majorConflict = true; break; }
            }

            if (majorConflict) {
                result.put("success", true);
                result.put("targetDay", targetDay);
                result.put("targetSlot", targetSlot);
                result.put("allowedBuildings", allowedBuildings);
                result.put("availableRooms", List.of());
                result.put("message", "该时段有合班专业(" + String.join("、", allMajors) + ")的课程冲突");
                return result;
            }

            // 查询在目标时间段已经被占用的教室
            List<String> occupiedRooms = jdbc.queryForList(
                "SELECT DISTINCT classroom FROM course " +
                "WHERE schedule_day = ? AND schedule_time = ?",
                String.class, targetDay, targetSlot);

            // 查询所有允许的教学楼中未被占用的教室
            String inClause = String.join(",", Collections.nCopies(allowedBuildings.size(), "?"));
            String sql = "SELECT code, building, room_no, capacity, has_ac, has_multimedia, notes " +
                        "FROM classroom WHERE building IN (" + inClause + ")";
            if (!occupiedRooms.isEmpty()) {
                String notInClause = String.join(",", Collections.nCopies(occupiedRooms.size(), "?"));
                sql += " AND code NOT IN (" + notInClause + ")";
            }
            sql += " ORDER BY building, room_no";

            List<Object> params = new ArrayList<>(allowedBuildings);
            params.addAll(occupiedRooms);
            List<Map<String, Object>> rooms = jdbc.queryForList(sql, params.toArray());

            result.put("success", true);
            result.put("targetDay", targetDay);
            result.put("targetSlot", targetSlot);
            result.put("allowedBuildings", allowedBuildings);
            result.put("availableRooms", rooms);
            if (allMajors.size() > 1) {
                result.put("combinedMajors", allMajors);
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "查询失败: " + e.getMessage());
        }
        return result;
    }

    // ==================== 提交调课申请 ====================
    @PostMapping("/adjust-course")
    public Map<String, Object> adjustCourse(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long courseId = Long.valueOf(body.get("courseId"));
            String reason = body.getOrDefault("reason", "");
            String targetDay = body.get("targetDay");
            String targetSlot = body.get("targetSlot");
            String targetRoom = body.get("targetRoom");
            String teacherNo = body.get("teacherNo");

            // 1. 验证课程存在且属于该教师
            List<Map<String, Object>> courseInfo = jdbc.queryForList(
                "SELECT c.*, t.name AS teacher_name, t.teacher_no " +
                "FROM course c JOIN teacher t ON c.teacher_id = t.id " +
                "WHERE c.id = ? AND t.teacher_no = ?", courseId, teacherNo);
            if (courseInfo.isEmpty()) {
                result.put("success", false);
                result.put("message", "课程不存在或不属于当前教师");
                return result;
            }
            Map<String, Object> course = courseInfo.get(0);
            String originalDay = (String) course.get("schedule_day");
            String originalSlot = (String) course.get("schedule_time");
            String originalRoom = (String) course.get("classroom");
            String courseName = (String) course.get("course_name");
            String major = (String) course.get("major");
            String teacherName = (String) course.get("teacher_name");

            // 2. 验证目标教室可用
            List<Map<String, Object>> conflict = jdbc.queryForList(
                "SELECT id FROM course WHERE schedule_day = ? AND schedule_time = ? AND classroom = ? AND id != ?",
                targetDay, targetSlot, targetRoom, courseId);
            if (!conflict.isEmpty()) {
                result.put("success", false);
                result.put("message", "目标教室在目标时间已被占用");
                return result;
            }

            // 3. 验证建筑规则
            List<Map<String, Object>> roomInfo = jdbc.queryForList(
                "SELECT building FROM classroom WHERE code = ?", targetRoom);
            if (roomInfo.isEmpty()) {
                result.put("success", false);
                result.put("message", "目标教室不存在");
                return result;
            }
            String targetBuilding = (String) roomInfo.get(0).get("building");
            List<String> allowedBuildings = getAllowedBuildings(major);
            if (!allowedBuildings.contains(targetBuilding)) {
                result.put("success", false);
                result.put("message", "不允许跨院楼调课：" + major + "只能在 " +
                        String.join("、", allowedBuildings) + " 中调课");
                return result;
            }

            // 4. 更新课程表
            jdbc.update(
                "UPDATE course SET schedule_day = ?, schedule_time = ?, classroom = ? WHERE id = ?",
                targetDay, targetSlot, targetRoom, courseId);

            // 5. 发布调课通知给对应专业的学生
            String noticeTitle = "📢 调课通知";
            String noticeContent = String.format(
                "【调课通知】%s 老师的《%s》课程已调整：\n" +
                "原时间：%s %s @%s\n" +
                "新时间：%s %s @%s\n" +
                "调整原因：%s\n" +
                "请 %s 专业的同学注意查看最新课表。",
                teacherName, courseName, originalDay, originalSlot, originalRoom,
                targetDay, targetSlot, targetRoom,
                reason.isEmpty() ? "教学安排调整" : reason, major);
            jdbc.update(
                "INSERT INTO notice (title, content, target_major, publish_time) VALUES (?, ?, ?, ?)",
                noticeTitle, noticeContent, major, LocalDateTime.now());

            result.put("success", true);
            result.put("message", "调课成功！已通知 " + major + " 专业的学生。");
            result.put("detail", Map.of(
                "courseName", courseName,
                "major", major,
                "originalDay", originalDay,
                "originalSlot", originalSlot,
                "originalRoom", originalRoom,
                "targetDay", targetDay,
                "targetSlot", targetSlot,
                "targetRoom", targetRoom
            ));
        } catch (Exception e) {
            log.error("调课失败", e);
            result.put("success", false);
            result.put("message", "调课失败: " + e.getMessage());
        }
        return result;
    }

    // ==================== 获取专业对应的可选教学楼 ====================
    private List<String> getAllowedBuildings(String major) {
        // 计科学院专业（物联网工程、计算机科学）→ 至真楼 + 博学楼
        // 法学院专业（法学）→ 明法楼 + 博学楼
        // 其他专业（如金融）→ 明法楼 + 博学楼（默认同法学院）
        Set<String> csBuildings = new LinkedHashSet<>(List.of("至真楼", "博学楼"));
        Set<String> lawBuildings = new LinkedHashSet<>(List.of("明法楼", "博学楼"));

        if ("物联网工程".equals(major) || "计算机科学".equals(major)) {
            return new ArrayList<>(csBuildings);
        } else if ("法学".equals(major) || "金融".equals(major)) {
            return new ArrayList<>(lawBuildings);
        }
        // 默认允许所有教学楼
        return new ArrayList<>(Set.of("至真楼", "明法楼", "博学楼"));
    }
}