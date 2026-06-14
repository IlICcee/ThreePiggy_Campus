package com.example.model;

public class Course {
    private Long id;
    private String courseCode;  // 课程编号
    private String courseName;  // 课程名称
    private String major;       // 所属专业
    private String semester;    // 学期，如 2025-2026-2
    private Double credits;     // 学分
    private String teacher;     // 授课教师
    private String scheduleTime;// 上课时间
    private String classroom;   // 上课地点

    public Course() {}

    public Course(Long id, String courseCode, String courseName, String major,
                  String semester, Double credits, String teacher,
                  String scheduleTime, String classroom) {
        this.id = id;
        this.courseCode = courseCode;
        this.courseName = courseName;
        this.major = major;
        this.semester = semester;
        this.credits = credits;
        this.teacher = teacher;
        this.scheduleTime = scheduleTime;
        this.classroom = classroom;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode; }

    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }

    public String getMajor() { return major; }
    public void setMajor(String major) { this.major = major; }

    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }

    public Double getCredits() { return credits; }
    public void setCredits(Double credits) { this.credits = credits; }

    public String getTeacher() { return teacher; }
    public void setTeacher(String teacher) { this.teacher = teacher; }

    public String getScheduleTime() { return scheduleTime; }
    public void setScheduleTime(String scheduleTime) { this.scheduleTime = scheduleTime; }

    public String getClassroom() { return classroom; }
    public void setClassroom(String classroom) { this.classroom = classroom; }
}
