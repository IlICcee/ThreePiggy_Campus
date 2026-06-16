package com.example.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 统一登录：支持教师号 / 学号 + 密码登录
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    private final JdbcTemplate jdbc;

    public AuthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req) {
        Map<String, Object> result = new HashMap<>();
        String id = req.getUsername().trim();
        String pw = req.getPassword().trim();

        // 1) 先查教师表
        List<Map<String, Object>> teachers = jdbc.queryForList(
            "SELECT id, teacher_no, name, 'teacher' AS role FROM teacher WHERE teacher_no = ? AND password = ?", id, pw);
        if (!teachers.isEmpty()) {
            result.put("success", true);
            result.put("user", teachers.get(0));
            result.put("message", "登录成功！欢迎" + teachers.get(0).get("name") + "老师");
            return result;
        }

        // 2) 再查学生表
        List<Map<String, Object>> students = jdbc.queryForList(
            "SELECT id, student_no, name, major, class_name, 'student' AS role FROM student WHERE student_no = ? AND password = ?", id, pw);
        if (!students.isEmpty()) {
            result.put("success", true);
            result.put("user", students.get(0));
            result.put("message", "登录成功！欢迎" + students.get(0).get("name") + "同学");
            return result;
        }

        result.put("success", false);
        result.put("message", "账号或密码错误");
        return result;
    }

    public static class LoginRequest {
        private String username;
        private String password;
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
