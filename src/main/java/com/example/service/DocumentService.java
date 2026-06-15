package com.example.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * 知识库文档导入服务
 *
 * 工作流程：
 * 1. 扫描 knowledge-base/ 目录下的 .txt / .md / .html 文件
 * 2. 读取文件内容
 * 3. 按段落分块（每块约 500 字符，块间有重叠）
 * 4. 向量化并存入 PostgreSQL pgvector（表名: vector_store）
 *
 * PDF / Word 文件需引入 Apache Tika 依赖后方可支持
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    /** 知识库文件目录 */
    private static final Path KNOWLEDGE_BASE = Path.of("knowledge-base");

    /** 分块最大字符数（中文约 300 token ≈ 450 字符） */
    private static final int CHUNK_MAX_LENGTH = 500;

    /** 相邻块的重叠字符数（避免关键信息被切断） */
    private static final int CHUNK_OVERLAP = 80;

    private final PgVectorStore vectorStore;

    public DocumentService(PgVectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    // ==================== 核心：导入目录中所有文档 ====================

    /**
     * 扫描 knowledge-base/ 目录，导入所有支持的文档
     * @return 导入报告
     */
    public Map<String, Object> importAllDocuments() {
        Map<String, Object> report = new LinkedHashMap<>();
        int totalFiles = 0;
        int totalChunks = 0;
        List<String> imported = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        try {
            // 确保目录存在
            if (!Files.exists(KNOWLEDGE_BASE)) {
                Files.createDirectories(KNOWLEDGE_BASE);
                report.put("success", true);
                report.put("message", "知识库目录已创建（空），请将文档放入 knowledge-base/ 目录后重新导入");
                report.put("importedFiles", 0);
                report.put("totalChunks", 0);
                return report;
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(KNOWLEDGE_BASE)) {
                for (Path filePath : stream) {
                    if (!Files.isRegularFile(filePath)) continue;

                    String fileName = filePath.getFileName().toString();
                    String ext = fileName.contains(".")
                            ? fileName.substring(fileName.lastIndexOf('.')).toLowerCase()
                            : "";

                    try {
                        String content = readFile(filePath, ext);
                        if (content == null || content.isBlank()) {
                            continue;
                        }

                        // 按段落分块
                        List<Document> chunks = splitIntoChunks(content, fileName, CHUNK_MAX_LENGTH, CHUNK_OVERLAP);

                        // 向量化 + 入库
                        vectorStore.add(chunks);

                        totalFiles++;
                        totalChunks += chunks.size();
                        imported.add(fileName + " → " + chunks.size() + " 个向量块");
                        log.info("已导入: {} ({} 块)", fileName, chunks.size());

                    } catch (Exception e) {
                        log.error("导入失败: {} - {}", fileName, e.getMessage());
                        failed.add(fileName + ": " + e.getMessage());
                    }
                }
            }

            report.put("success", true);
            report.put("importedFiles", totalFiles);
            report.put("totalChunks", totalChunks);
            report.put("detail", imported);
            if (!failed.isEmpty()) {
                report.put("failed", failed);
            }

        } catch (IOException e) {
            report.put("success", false);
            report.put("message", "导入异常: " + e.getMessage());
        }

        return report;
    }

    // ==================== 文档分块 ====================

    /**
     * 将长文本按段落拆分为重叠的小块
     */
    private List<Document> splitIntoChunks(String text, String fileName, int maxLen, int overlap) {
        List<Document> chunks = new ArrayList<>();

        // 先按段落分割
        String[] paragraphs = text.split("\\n\\s*\\n");
        StringBuilder currentChunk = new StringBuilder();
        String prevOverlap = ""; // 上一块的尾部文本作为重叠

        for (int i = 0; i < paragraphs.length; i++) {
            String para = paragraphs[i].strip();
            if (para.isEmpty()) continue;

            if (currentChunk.length() + para.length() > maxLen && currentChunk.length() > 0) {
                // 当前块已满，存入
                chunks.add(createChunk(currentChunk.toString(), fileName, chunks.size()));

                // 保留尾部作为下一个块的重叠
                String chunkText = currentChunk.toString();
                if (chunkText.length() > overlap) {
                    prevOverlap = chunkText.substring(chunkText.length() - overlap);
                } else {
                    prevOverlap = chunkText;
                }
                currentChunk = new StringBuilder(prevOverlap.isEmpty() ? "" : prevOverlap + "\n\n");
            }

            if (currentChunk.length() > 0) {
                currentChunk.append(para).append("\n\n");
            } else {
                currentChunk.append(para).append("\n\n");
            }
        }

        // 最后一块
        if (currentChunk.length() > 0) {
            chunks.add(createChunk(currentChunk.toString(), fileName, chunks.size()));
        }

        // 如果没分出块（单段落、无空行），整个文本作为一块
        if (chunks.isEmpty()) {
            chunks.add(createChunk(text, fileName, 0));
        }

        return chunks;
    }

    private Document createChunk(String content, String fileName, int index) {
        return new Document(content.trim(),
                Map.of("source", fileName, "chunk_index", index));
    }

    // ==================== 文件读取 ====================

    /**
     * 根据扩展名读取文件内容
     */
    private String readFile(Path filePath, String ext) throws IOException {
        return switch (ext) {
            case ".txt"  -> Files.readString(filePath, StandardCharsets.UTF_8);
            case ".md"   -> Files.readString(filePath, StandardCharsets.UTF_8);
            case ".html" -> Files.readString(filePath, StandardCharsets.UTF_8);
            case ".pdf"  -> {
                log.warn("PDF 需 Apache Tika 依赖，暂不支持: {}", filePath.getFileName());
                yield null;
            }
            case ".docx", ".doc" -> {
                log.warn("Word 需 Apache Tika 依赖，暂不支持: {}", filePath.getFileName());
                yield null;
            }
            default -> {
                log.warn("不支持的格式: {} ({})", filePath.getFileName(), ext);
                yield null;
            }
        };
    }
}
