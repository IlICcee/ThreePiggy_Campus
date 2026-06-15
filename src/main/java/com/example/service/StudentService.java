package com.example.service;

import com.example.model.Course;
import com.example.model.Grade;
import com.example.model.Student;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class StudentService {

    private static final Logger log = LoggerFactory.getLogger(StudentService.class);
    private static final String CURRENT_SEMESTER = "2025-2026-2";

    private final JdbcTemplate jdbc;

    public StudentService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ==================== 登录 ====================
    public Student login(String studentNo, String password) {
        String sql = "SELECT id, student_no, name, password, major, class_name, enrollment_year " +
                     "FROM student WHERE student_no = ? AND password = ?";
        List<Student> list = jdbc.query(sql, STUDENT_MAPPER, studentNo, password);
        if (list.isEmpty()) {
            return null;
        }
        Student s = list.get(0);
        s.setPassword(null); // 不返回密码
        return s;
    }

    public Student getStudentById(Long studentId) {
        String sql = "SELECT id, student_no, name, password, major, class_name, enrollment_year " +
                     "FROM student WHERE id = ?";
        List<Student> list = jdbc.query(sql, STUDENT_MAPPER, studentId);
        if (list.isEmpty()) return null;
        Student s = list.get(0);
        s.setPassword(null);
        return s;
    }

    // ==================== 成绩查询 ====================
    public List<Grade> getGrades(Long studentId) {
        String sql = """
            SELECT g.id, g.student_id, g.course_id, g.score, g.grade_point, g.semester,
                   c.course_name, c.credits
            FROM grade g
            JOIN course c ON g.course_id = c.id
            WHERE g.student_id = ? AND g.semester = ?
            ORDER BY c.course_code
            """;
        return jdbc.query(sql, GRADE_DETAIL_MAPPER, studentId, CURRENT_SEMESTER);
    }

    // ==================== 课表查询 ====================
    public List<Course> getCourses(Long studentId) {
        // 先获取学生专业
        Student student = getStudentById(studentId);
        if (student == null) return List.of();

        String sql = """
            SELECT id, course_code, course_name, major, semester, credits,
                   teacher, schedule_time, classroom
            FROM course
            WHERE major = ? AND semester = ?
            ORDER BY course_code
            """;
        return jdbc.query(sql, COURSE_MAPPER, student.getMajor(), CURRENT_SEMESTER);
    }

    // ==================== 学分查询 ====================
    public Map<String, Object> getCredits(Long studentId) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 本学期在修课程学分
        Student student = getStudentById(studentId);
        if (student == null) return result;

        Double currentSemesterCredits = jdbc.queryForObject(
            "SELECT COALESCE(SUM(credits), 0) FROM course WHERE major = ? AND semester = ?",
            Double.class, student.getMajor(), CURRENT_SEMESTER
        );

        // 已获得学分（该生所有及格课程的学分）
        Double earnedCredits = jdbc.queryForObject(
            "SELECT COALESCE(SUM(c.credits), 0) FROM grade g JOIN course c ON g.course_id = c.id WHERE g.student_id = ? AND g.score >= 60",
            Double.class, studentId
        );

        // 本学期已获得学分
        Double currentEarnedCredits = jdbc.queryForObject(
            "SELECT COALESCE(SUM(c.credits), 0) FROM grade g JOIN course c ON g.course_id = c.id WHERE g.student_id = ? AND g.semester = ? AND g.score >= 60",
            Double.class, studentId, CURRENT_SEMESTER
        );

        result.put("studentName", student.getName());
        result.put("major", student.getMajor());
        result.put("totalEarnedCredits", earnedCredits != null ? earnedCredits : 0.0);
        result.put("currentSemesterCredits", currentSemesterCredits != null ? currentSemesterCredits : 0.0);
        result.put("currentEarnedCredits", currentEarnedCredits != null ? currentEarnedCredits : 0.0);

        return result;
    }

    // ==================== 专业排名 ====================
    public Map<String, Object> getRanking(Long studentId) {
        Map<String, Object> result = new LinkedHashMap<>();

        Student student = getStudentById(studentId);
        if (student == null) return result;

        // 计算本专业所有学生的绩点（加权平均）
        String gpaSql = """
            SELECT g.student_id,
                   ROUND(SUM(g.grade_point * c.credits) / SUM(c.credits), 2) AS gpa
            FROM grade g
            JOIN course c ON g.course_id = c.id
            JOIN student s ON g.student_id = s.id
            WHERE s.major = ? AND g.semester = ?
            GROUP BY g.student_id
            ORDER BY gpa DESC
            """;

        List<Map<String, Object>> rankings = jdbc.queryForList(gpaSql, student.getMajor(), CURRENT_SEMESTER);

        int rank = 0;
        double myGpa = 0.0;
        for (int i = 0; i < rankings.size(); i++) {
            Map<String, Object> row = rankings.get(i);
            Long sid = ((Number) row.get("student_id")).longValue();
            if (sid.equals(studentId)) {
                rank = i + 1;
                myGpa = ((Number) row.get("gpa")).doubleValue();
            }
        }

        result.put("studentName", student.getName());
        result.put("major", student.getMajor());
        result.put("semester", CURRENT_SEMESTER);
        result.put("totalStudents", rankings.size());
        result.put("rank", rank);
        result.put("gpa", myGpa);

        // 前5名
        List<Map<String, Object>> top5 = new ArrayList<>();
        for (int i = 0; i < Math.min(5, rankings.size()); i++) {
            Map<String, Object> row = rankings.get(i);
            Long sid = ((Number) row.get("student_id")).longValue();
            Student s = getStudentById(sid);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("rank", i + 1);
            entry.put("name", s != null ? s.getName() : "未知");
            entry.put("studentNo", s != null ? s.getStudentNo() : "");
            entry.put("gpa", row.get("gpa"));
            top5.add(entry);
        }
        result.put("top5", top5);

        return result;
    }

    // ==================== 获取同专业学生列表（供 AI 工具使用） ====================
    public List<Student> getStudentsByMajor(String major) {
        String sql = "SELECT id, student_no, name, password, major, class_name, enrollment_year FROM student WHERE major = ?";
        return jdbc.query(sql, STUDENT_MAPPER, major);
    }

    // ==================== RowMapper ====================
    private static final RowMapper<Student> STUDENT_MAPPER = (rs, rowNum) -> {
        Student s = new Student();
        s.setId(rs.getLong("id"));
        s.setStudentNo(rs.getString("student_no"));
        s.setName(rs.getString("name"));
        s.setPassword(rs.getString("password"));
        s.setMajor(rs.getString("major"));
        s.setClassName(rs.getString("class_name"));
        s.setEnrollmentYear(rs.getInt("enrollment_year"));
        return s;
    };

    private static final RowMapper<Course> COURSE_MAPPER = (rs, rowNum) -> {
        Course c = new Course();
        c.setId(rs.getLong("id"));
        c.setCourseCode(rs.getString("course_code"));
        c.setCourseName(rs.getString("course_name"));
        c.setMajor(rs.getString("major"));
        c.setSemester(rs.getString("semester"));
        c.setCredits(rs.getDouble("credits"));
        c.setTeacher(rs.getString("teacher"));
        c.setScheduleTime(rs.getString("schedule_time"));
        c.setClassroom(rs.getString("classroom"));
        return c;
    };

    private static final RowMapper<Grade> GRADE_DETAIL_MAPPER = (rs, rowNum) -> {
        Grade g = new Grade();
        g.setId(rs.getLong("id"));
        g.setStudentId(rs.getLong("student_id"));
        g.setCourseId(rs.getLong("course_id"));
        g.setScore(rs.getDouble("score"));
        g.setGradePoint(rs.getDouble("grade_point"));
        g.setSemester(rs.getString("semester"));
        g.setCourseName(rs.getString("course_name"));
        g.setCredits(rs.getDouble("credits"));
        return g;
    };
}
