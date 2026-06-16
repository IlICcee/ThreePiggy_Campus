package com.example.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class CampusTool {

    private static final Logger log = LoggerFactory.getLogger(CampusTool.class);
    private static final List<String> WEEK_DAYS = List.of("周一","周二","周三","周四","周五");
    private static final List<String> TIME_SLOTS = List.of("1-2节","3-4节","5-6节","7-8节");

    private final JdbcTemplate jdbc;

    public CampusTool(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Tool(description = "查询最近的校园公告")
    public String getRecentNotices() {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT title, content FROM notice ORDER BY publish_time DESC LIMIT 5");
            if (rows.isEmpty()) return "暂无公告";
            return rows.stream()
                    .map(r -> "【" + r.get("title") + "】" + r.get("content"))
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "公告查询失败: " + e.getMessage();
        }
    }

    @Tool(description = "查询某门课可调到的空闲时间与教室。参数: teacherNo教师编号, courseName课程名, originalDay原星期几(如周一), originalSlot原节次(如1-2节)")
    public String checkCourseAdjustment(String teacherNo, String courseName, String originalDay, String originalSlot) {
        try {
            // 1. 找到该课程及所有合班专业
            Long tid = jdbc.queryForObject("SELECT id FROM teacher WHERE teacher_no = ?", Long.class, teacherNo);
            List<Map<String, Object>> courses = jdbc.queryForList(
                "SELECT id, course_name, major FROM course " +
                "WHERE teacher_id = ? AND course_name = ? AND schedule_day = ? AND schedule_time = ?",
                tid, courseName, originalDay, originalSlot);
            if (courses.isEmpty()) {
                // 尝试模糊匹配
                courses = jdbc.queryForList(
                    "SELECT id, course_name, major FROM course " +
                    "WHERE teacher_id = ? AND course_name LIKE ? AND schedule_day = ? AND schedule_time = ?",
                    tid, "%" + courseName + "%", originalDay, originalSlot);
            }
            if (courses.isEmpty()) return "未找到课程：" + courseName + "（" + originalDay + " " + originalSlot + "）";

            // 获取所有合班专业
            String cn = (String) courses.get(0).get("course_name");
            List<String> allMajors = jdbc.queryForList(
                "SELECT DISTINCT major FROM course WHERE teacher_id = ? AND course_name = ?",
                String.class, tid, cn);

            // 建筑规则
            List<String> allowedBuildings = getAllowedBuildings(allMajors.get(0));

            // 2. 遍历所有可能的时段
            StringBuilder sb = new StringBuilder();
            sb.append("课程《").append(cn).append("》（合班专业：").append(String.join("、", allMajors))
              .append("）可调整的时段与教室：\n\n");

            boolean foundAny = false;
            for (String day : WEEK_DAYS) {
                boolean dayHasSlot = false;
                StringBuilder daySb = new StringBuilder();
                for (String slot : TIME_SLOTS) {
                    if (day.equals(originalDay) && slot.equals(originalSlot)) continue;
                    // 检查所有合班专业在该时段是否有课
                    boolean conflict = false;
                    for (String m : allMajors) {
                        int cnt = jdbc.queryForObject(
                            "SELECT COUNT(*) FROM course WHERE major = ? AND schedule_day = ? AND schedule_time = ?",
                            Integer.class, m, day, slot);
                        if (cnt > 0) { conflict = true; break; }
                    }
                    if (conflict) continue;

                    // 查该时段可用教室
                    List<String> occupied = jdbc.queryForList(
                        "SELECT DISTINCT classroom FROM course WHERE schedule_day = ? AND schedule_time = ?",
                        String.class, day, slot);
                    String bIn = allowedBuildings.stream().map(b -> "?").collect(Collectors.joining(","));
                    List<Object> params = new ArrayList<>(allowedBuildings);
                    String sql = "SELECT code, room_no, capacity, has_ac, has_multimedia, notes FROM classroom WHERE building IN (" + bIn + ")";
                    if (!occupied.isEmpty()) {
                        String oNotIn = occupied.stream().map(o -> "?").collect(Collectors.joining(","));
                        sql += " AND code NOT IN (" + oNotIn + ")";
                        params.addAll(occupied);
                    }
                    List<Map<String, Object>> rooms = jdbc.queryForList(sql, params.toArray());
                    if (!rooms.isEmpty()) {
                        if (!dayHasSlot) { daySb.append("【").append(day).append("】\n"); dayHasSlot = true; }
                        daySb.append("  ").append(slot).append(": ");
                        for (Map<String, Object> r : rooms) {
                            daySb.append(r.get("code")).append("(").append(r.get("capacity")).append("人");
                            if (!(Boolean) r.get("has_ac")) daySb.append("无空调");
                            if (!(Boolean) r.get("has_multimedia")) daySb.append("多媒体坏");
                            String notes = (String) r.get("notes");
                            if (notes != null && !notes.isEmpty()) daySb.append(notes);
                            daySb.append(") ");
                        }
                        daySb.append("\n");
                        foundAny = true;
                    }
                }
                sb.append(daySb);
            }
            if (!foundAny) sb.append("暂无可调时段（所有时段均有冲突）。");
            return sb.toString();
        } catch (Exception e) {
            return "查询失败: " + e.getMessage();
        }
    }

    @Tool(description = "执行调课。参数: teacherNo教师编号, courseName课程名, originalDay/day原星期/节次, targetDay/targetSlot/targetRoom目标, reason原因")
    public String executeCourseAdjustment(String teacherNo, String courseName,
            String originalDay, String originalSlot, String targetDay, String targetSlot,
            String targetRoom, String reason) {
        try {
            Long tid = jdbc.queryForObject("SELECT id FROM teacher WHERE teacher_no = ?", Long.class, teacherNo);
            List<Map<String, Object>> courses = jdbc.queryForList(
                "SELECT c.id, c.course_name, c.major, cl.building " +
                "FROM course c LEFT JOIN classroom cl ON c.classroom = cl.code " +
                "WHERE c.teacher_id = ? AND c.course_name LIKE ? AND c.schedule_day = ? AND c.schedule_time = ?",
                tid, "%" + courseName + "%", originalDay, originalSlot);
            if (courses.isEmpty()) return "未找到课程";

            String cname = (String) courses.get(0).get("course_name");
            String exampleMajor = (String) courses.get(0).get("major");

            // 获取合班专业及所有待调课程ID
            List<String> allMajors = jdbc.queryForList(
                "SELECT DISTINCT major FROM course WHERE teacher_id = ? AND course_name = ?",
                String.class, tid, cname);
            List<Long> allCourseIds = jdbc.queryForList(
                "SELECT id FROM course WHERE teacher_id = ? AND course_name = ? AND schedule_day = ? AND schedule_time = ?",
                Long.class, tid, cname, originalDay, originalSlot);

            // 验证目标时段所有专业空闲（排除自身）
            for (String m : allMajors) {
                int cnt = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM course WHERE major = ? AND schedule_day = ? AND schedule_time = ? AND id NOT IN (" +
                    allCourseIds.stream().map(id -> "?").collect(Collectors.joining(",")) + ")",
                    Integer.class,
                    Stream.concat(Stream.of(m, targetDay, targetSlot), allCourseIds.stream()).toArray());
                if (cnt > 0) return "调课失败：" + m + "专业在" + targetDay + targetSlot + "已有课程";
            }

            // 验证教室可用（排除自身）
            Object[] roomParams = Stream.concat(
                Stream.of(targetDay, targetSlot, targetRoom), allCourseIds.stream()).toArray();
            int roomConflict = jdbc.queryForObject(
                "SELECT COUNT(*) FROM course WHERE schedule_day=? AND schedule_time=? AND classroom=? AND id NOT IN (" +
                allCourseIds.stream().map(id -> "?").collect(Collectors.joining(",")) + ")",
                Integer.class, roomParams);
            if (roomConflict > 0) return "调课失败：教室" + targetRoom + "已被占用";

            // 验证建筑规则
            List<String> allowed = getAllowedBuildings(exampleMajor);
            String targetBuilding = jdbc.queryForObject(
                "SELECT building FROM classroom WHERE code = ?", String.class, targetRoom);
            if (!allowed.contains(targetBuilding))
                return "调课失败：" + exampleMajor + "专业不能使用" + targetBuilding + "的教室";

            // 执行更新：合班课所有专业行一起调
            int updated = jdbc.update(
                "UPDATE course SET schedule_day=?, schedule_time=?, classroom=? " +
                "WHERE teacher_id=? AND course_name=? AND schedule_day=? AND schedule_time=?",
                targetDay, targetSlot, targetRoom, tid, cname, originalDay, originalSlot);

            // 通知所有合班专业
            for (String m : allMajors) {
                String notice = String.format("【调课通知】《%s》已从 %s %s 调整至 %s %s @%s。原因：%s。",
                        cname, originalDay, originalSlot, targetDay, targetSlot, targetRoom,
                        reason.isEmpty() ? "教学安排调整" : reason);
                jdbc.update("INSERT INTO notice (title, content, target_major, publish_time) VALUES (?,?,?,?)",
                        "调课通知", notice, m, LocalDateTime.now());
            }

            log.info("调课成功: {} {} {} -> {} {} {} ({} 行, {} 专业)", cname, originalDay, originalSlot,
                    targetDay, targetSlot, targetRoom, updated, String.join("、", allMajors));
            return "调课成功！《" + cname + "》已从" + originalDay + " " + originalSlot
                    + "调整至" + targetDay + " " + targetSlot + " @" + targetRoom
                    + "（合班：" + String.join("、", allMajors) + "），共更新" + updated + "条课程记录。";
        } catch (Exception e) {
            return "调课执行失败: " + e.getMessage();
        }
    }

    private List<String> getAllowedBuildings(String major) {
        if ("物联网工程".equals(major) || "计算机科学".equals(major))
            return List.of("至真楼", "博学楼");
        if ("法学".equals(major) || "金融".equals(major))
            return List.of("明法楼", "博学楼");
        return List.of("至真楼", "明法楼", "博学楼");
    }
}
