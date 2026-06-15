package com.example.controller;

import com.example.model.Course;
import com.example.model.Grade;
import com.example.model.Student;
import com.example.service.StudentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student")
public class StudentController {

    private static final Logger log = LoggerFactory.getLogger(StudentController.class);
    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    // ==================== 登录 ====================
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest request) {
        Map<String, Object> result = new HashMap<>();
        Student student = studentService.login(request.getStudentNo(), request.getPassword());
        if (student != null) {
            result.put("success", true);
            result.put("student", student);
            result.put("message", "登录成功，欢迎 " + student.getName() + "！");
        } else {
            result.put("success", false);
            result.put("message", "学号或密码错误，请重试");
        }
        return result;
    }

    // ==================== 获取学生信息 ====================
    @GetMapping("/info")
    public Map<String, Object> info(@RequestParam Long studentId) {
        Map<String, Object> result = new HashMap<>();
        Student student = studentService.getStudentById(studentId);
        if (student != null) {
            result.put("success", true);
            result.put("student", student);
        } else {
            result.put("success", false);
            result.put("message", "学生不存在");
        }
        return result;
    }

    // ==================== 查询成绩 ====================
    @GetMapping("/grades")
    public Map<String, Object> grades(@RequestParam Long studentId) {
        Map<String, Object> result = new HashMap<>();
        Student student = studentService.getStudentById(studentId);
        if (student == null) {
            result.put("success", false);
            result.put("message", "学生不存在");
            return result;
        }
        List<Grade> grades = studentService.getGrades(studentId);

        // 计算统计信息
        double totalCredits = grades.stream().mapToDouble(Grade::getCredits).sum();
        double weightedSum = 0;
        for (Grade g : grades) {
            weightedSum += g.getGradePoint() * g.getCredits();
        }
        double gpa = totalCredits > 0 ? Math.round(weightedSum / totalCredits * 100.0) / 100.0 : 0.0;

        result.put("success", true);
        result.put("studentName", student.getName());
        result.put("studentNo", student.getStudentNo());
        result.put("major", student.getMajor());
        result.put("semester", "2025-2026-2");
        result.put("grades", grades);
        result.put("gpa", gpa);
        result.put("totalCredits", totalCredits);
        return result;
    }

    // ==================== 查询课表 ====================
    @GetMapping("/courses")
    public Map<String, Object> courses(@RequestParam Long studentId) {
        Map<String, Object> result = new HashMap<>();
        Student student = studentService.getStudentById(studentId);
        if (student == null) {
            result.put("success", false);
            result.put("message", "学生不存在");
            return result;
        }
        List<Course> courses = studentService.getCourses(studentId);
        double totalCredits = courses.stream().mapToDouble(Course::getCredits).sum();

        result.put("success", true);
        result.put("studentName", student.getName());
        result.put("major", student.getMajor());
        result.put("semester", "2025-2026-2");
        result.put("courses", courses);
        result.put("totalCredits", totalCredits);
        result.put("courseCount", courses.size());
        return result;
    }

    // ==================== 学分汇总 ====================
    @GetMapping("/credits")
    public Map<String, Object> credits(@RequestParam Long studentId) {
        Map<String, Object> result = new HashMap<>();
        Student student = studentService.getStudentById(studentId);
        if (student == null) {
            result.put("success", false);
            result.put("message", "学生不存在");
            return result;
        }
        Map<String, Object> creditsData = studentService.getCredits(studentId);
        result.put("success", true);
        result.putAll(creditsData);
        return result;
    }

    // ==================== 专业排名 ====================
    @GetMapping("/ranking")
    public Map<String, Object> ranking(@RequestParam Long studentId) {
        Map<String, Object> result = new HashMap<>();
        Student student = studentService.getStudentById(studentId);
        if (student == null) {
            result.put("success", false);
            result.put("message", "学生不存在");
            return result;
        }
        Map<String, Object> rankingData = studentService.getRanking(studentId);
        result.put("success", true);
        result.putAll(rankingData);
        return result;
    }

    // ==================== 请求体类 ====================
    public static class LoginRequest {
        private String studentNo;
        private String password;

        public String getStudentNo() { return studentNo; }
        public void setStudentNo(String studentNo) { this.studentNo = studentNo; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
