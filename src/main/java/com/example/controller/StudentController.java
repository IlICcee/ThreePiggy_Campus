package com.example.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/student")
public class StudentController {

    private static final Logger log = LoggerFactory.getLogger(StudentController.class);
    private final JdbcTemplate jdbc;

    public StudentController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ==================== 学生课表（增强版：含教师、教室设施） ====================
    @GetMapping("/courses")
    public Map<String, Object> courses(@RequestParam String studentNo) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> stuList = jdbc.queryForList(
                "SELECT name, major, class_name FROM student WHERE student_no = ?", studentNo);
            if (stuList.isEmpty()) {
                result.put("success", false); result.put("message", "学生不存在"); return result;
            }
            String major = (String) stuList.get(0).get("major");

            List<Map<String, Object>> courses = jdbc.queryForList(
                "SELECT c.id, c.course_name, t.name AS teacher, c.schedule_day, c.schedule_time, " +
                "c.start_time, c.classroom, c.credits, " +
                "r.has_ac, r.has_multimedia, r.notes " +
                "FROM course c " +
                "LEFT JOIN teacher t ON c.teacher_id = t.id " +
                "LEFT JOIN classroom r ON c.classroom = r.code " +
                "WHERE c.major = ? ORDER BY c.schedule_day, c.schedule_time", major);

            result.put("success", true);
            result.put("name", stuList.get(0).get("name"));
            result.put("major", major);
            result.put("class_name", stuList.get(0).get("class_name"));
            result.put("courses", courses);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "查询失败: " + e.getMessage());
        }
        return result;
    }

    // ==================== 学生成绩 ====================
    @GetMapping("/grades")
    public Map<String, Object> grades(@RequestParam String studentNo) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> stuList = jdbc.queryForList(
                "SELECT name, major FROM student WHERE student_no = ?", studentNo);
            if (stuList.isEmpty()) {
                result.put("success", false); result.put("message", "学生不存在"); return result;
            }

            List<Map<String, Object>> grades = jdbc.queryForList(
                "SELECT c.course_name, g.score, g.grade_point, c.credits " +
                "FROM grade g JOIN course c ON g.course_id = c.id " +
                "JOIN student s ON g.student_id = s.id " +
                "WHERE s.student_no = ?", studentNo);

            double totalCredits = 0, weightedSum = 0;
            for (Map<String, Object> g : grades) {
                double gp = ((Number) g.get("grade_point")).doubleValue();
                double cr = ((Number) g.get("credits")).doubleValue();
                weightedSum += gp * cr;
                totalCredits += cr;
            }
            double gpa = totalCredits > 0 ? Math.round(weightedSum / totalCredits * 100.0) / 100.0 : 0.0;

            result.put("success", true);
            result.put("name", stuList.get(0).get("name"));
            result.put("grades", grades);
            result.put("gpa", gpa);
            result.put("totalCredits", totalCredits);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "查询失败: " + e.getMessage());
        }
        return result;
    }
}