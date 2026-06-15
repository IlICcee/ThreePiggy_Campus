package com.example.model;

public class Student {
    private Long id;
    private String studentNo;   // 学号
    private String name;        // 姓名
    private String password;    // 密码
    private String major;       // 专业
    private String className;   // 班级
    private Integer enrollmentYear; // 入学年份

    public Student() {}

    public Student(Long id, String studentNo, String name, String password,
                   String major, String className, Integer enrollmentYear) {
        this.id = id;
        this.studentNo = studentNo;
        this.name = name;
        this.password = password;
        this.major = major;
        this.className = className;
        this.enrollmentYear = enrollmentYear;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getStudentNo() { return studentNo; }
    public void setStudentNo(String studentNo) { this.studentNo = studentNo; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getMajor() { return major; }
    public void setMajor(String major) { this.major = major; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public Integer getEnrollmentYear() { return enrollmentYear; }
    public void setEnrollmentYear(Integer enrollmentYear) { this.enrollmentYear = enrollmentYear; }
}
