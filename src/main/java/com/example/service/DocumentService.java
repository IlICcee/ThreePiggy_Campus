package com.example.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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
    private static final int CHUNK_MAX_LENGTH = 200;

    /** 相邻块的重叠字符数（避免关键信息被切断） */
    private static final int CHUNK_OVERLAP = 80;

    /** 每批写入向量库的块数（避免 OOM） */
    private static final int BATCH_SIZE = 5;

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

                        // 分批向量化 + 入库（避免 OOM）
                        for (int i = 0; i < chunks.size(); i += BATCH_SIZE) {
                            int end = Math.min(i + BATCH_SIZE, chunks.size());
                            vectorStore.add(chunks.subList(i, end));
                            log.info("  批次 {}/{}: {} 块", (i / BATCH_SIZE) + 1,
                                    (chunks.size() + BATCH_SIZE - 1) / BATCH_SIZE, end - i);
                        }

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
     * 将长文本拆分为重叠的小块
     * 策略：优先按段落分，段落过长则按 maxLen 强制切分
     */
    private List<Document> splitIntoChunks(String text, String fileName, int maxLen, int overlap) {
        List<Document> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();

        // 统一换行符，并在中文标点后插入分段提示
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        // 只在存在空行时才按段落分，否则按字符数硬切
        String[] paragraphs = normalized.split("\\n\\s*\\n");

        for (String para : paragraphs) {
            String p = para.strip();
            if (p.isEmpty()) continue;

            // 如果当前段落本身就很长，先切成小段
            List<String> subChunks = splitLongText(p, maxLen, overlap, fileName);

            for (String sub : subChunks) {
                if (currentChunk.length() + sub.length() > maxLen && currentChunk.length() > 0) {
                    chunks.add(createChunk(currentChunk.toString(), fileName, chunks.size()));
                    currentChunk = new StringBuilder();
                }
                if (currentChunk.length() > 0) {
                    currentChunk.append(sub).append("\n\n");
                } else {
                    currentChunk.append(sub).append("\n\n");
                }
            }
        }

        if (currentChunk.length() > 0) {
            chunks.add(createChunk(currentChunk.toString(), fileName, chunks.size()));
        }

        if (chunks.isEmpty()) {
            chunks.add(createChunk(text, fileName, 0));
        }

        return chunks;
    }

    /**
     * 将过长文本按 maxLen 强制切分，块间有 overlap 重叠
     */
    private List<String> splitLongText(String text, int maxLen, int overlap, String fileName) {
        List<String> result = new ArrayList<>();
        if (text.length() <= maxLen) {
            result.add(text);
            return result;
        }
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxLen, text.length());
            // 尝试在标点处断开，避免切断句子
            if (end < text.length()) {
                int cutPoint = findCutPoint(text, end, start + maxLen / 2);
                end = cutPoint;
            }
            result.add(text.substring(start, end));
            start = end - overlap; // 重叠
            if (start >= text.length()) break;
            if (start < 0) start = 0;
        }
        return result;
    }

    /**
     * 在 [minPos, end] 范围内找最佳切分点（句号、换行等）
     */
    private int findCutPoint(String text, int end, int minPos) {
        for (int i = end; i >= minPos; i--) {
            char c = text.charAt(i);
            if (c == '\n' || c == '。' || c == '！' || c == '？' || c == '；' || c == ' ') {
                return i + 1;
            }
        }
        return end;
    }

    private Document createChunk(String content, String fileName, int index) {
        return new Document(content.trim(),
                Map.of("source", fileName, "chunk_index", index));
    }

    // ==================== 文件读取 ====================

    /**
     * 读取 PDF 文件，提取纯文本
     */
    private String readPdf(Path filePath) {
        try (PDDocument pdfDoc = Loader.loadPDF(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(false);
            String text = stripper.getText(pdfDoc);
            int pages = pdfDoc.getNumberOfPages();
            pdfDoc.close();
            log.info("PDF 解析成功: {} ({} 页, {} 字符)",
                    filePath.getFileName(), pages, text.length());
            return text;
        } catch (IOException e) {
            log.error("PDF 读取失败: {} - {}", filePath.getFileName(), e.getMessage());
            return null;
        }
    }

    /**
     * 根据扩展名读取文件内容
     */
    private String readFile(Path filePath, String ext) throws IOException {
        return switch (ext) {
            case ".txt"  -> Files.readString(filePath, StandardCharsets.UTF_8);
            case ".md"   -> Files.readString(filePath, StandardCharsets.UTF_8);
            case ".html" -> Files.readString(filePath, StandardCharsets.UTF_8);
            case ".pdf"  -> readPdf(filePath);
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
