package com.example.model;

public class Grade {
    private Long id;
    private Long studentId;     // 学生ID
    private Long courseId;      // 课程ID
    private Double score;       // 分数
    private Double gradePoint;  // 绩点
    private String semester;    // 学期

    // 关联查询用（非数据库字段）
    private String courseName;
    private Double credits;

    public Grade() {}

    public Grade(Long id, Long studentId, Long courseId, Double score,
                 Double gradePoint, String semester) {
        this.id = id;
        this.studentId = studentId;
        this.courseId = courseId;
        this.score = score;
        this.gradePoint = gradePoint;
        this.semester = semester;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }

    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }

    public Double getGradePoint() { return gradePoint; }
    public void setGradePoint(Double gradePoint) { this.gradePoint = gradePoint; }

    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }

    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }

    public Double getCredits() { return credits; }
    public void setCredits(Double credits) { this.credits = credits; }
}
