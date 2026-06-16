package com.example.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/teacher")
public class TeacherController {

    private static final Logger log = LoggerFactory.getLogger(TeacherController.class);
    private final JdbcTemplate jdbc;

    public TeacherController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ==================== 教师授课班级 ====================
    @GetMapping("/classes")
    public Map<String, Object> classes(@RequestParam String teacherNo) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 查该教师所教课程覆盖的专业 → 对应班级名
            List<Map<String, Object>> list = jdbc.queryForList(
                "SELECT DISTINCT s.class_name FROM course c " +
                "JOIN student s ON c.major = s.major " +
                "WHERE c.teacher_id = (SELECT id FROM teacher WHERE teacher_no = ?) " +
                "ORDER BY s.class_name", teacherNo);
            result.put("success", true);
            result.put("classes", list.stream().map(m -> m.get("class_name")).toList());
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "查询失败: " + e.getMessage());
        }
        return result;
    }

    // ==================== 教师课表 ====================
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
                "SELECT course_name, major, schedule_day, schedule_time, classroom " +
                "FROM course WHERE teacher_id = ? ORDER BY schedule_day, schedule_time", tid);

            result.put("success", true);
            result.put("name", teaList.get(0).get("name"));
            result.put("courses", courses);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "查询失败: " + e.getMessage());
        }
        return result;
    }
}
