package com.example.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class CampusTool {

    private static final Logger log = LoggerFactory.getLogger(CampusTool.class);

    private final JdbcTemplate jdbcTemplate;

    public CampusTool(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Tool(description = "查询最近的校园公告，返回公告标题和内容列表")
    public String getRecentNotices() {
        try {
            // H2 MSSQL 兼容模式下支持 TOP，SQL Server 原生支持 TOP
            String sql = "SELECT TOP 5 title, content FROM notice ORDER BY publish_time DESC";

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            if (rows.isEmpty()) {
                return "暂无公告";
            }
            return rows.stream()
                    .map(row -> "【" + row.get("title") + "】" + row.get("content"))
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.error("查询公告失败", e);
            return "公告查询失败: " + e.getMessage();
        }
    }
}