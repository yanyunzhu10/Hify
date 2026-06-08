package com.hify.modules.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.http.EmbeddingClient;
import com.hify.modules.knowledge.dto.ChunkResp;
import com.hify.modules.knowledge.dto.DocumentResp;
import com.hify.modules.knowledge.entity.Document;
import com.hify.modules.knowledge.mapper.DocumentMapper;
import com.hify.modules.knowledge.repository.ChunkRepository;
import com.hify.modules.knowledge.repository.ChunkRow;
import com.hify.modules.knowledge.service.DocumentService;
import com.hify.modules.knowledge.service.KnowledgeBaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DocumentServiceImpl implements DocumentService {

    private static final Set<String> ALLOWED_EXT = Set.of("txt", "md", "pdf");
    private static final long MAX_SIZE = 10 * 1024 * 1024L; // 10 MB
    private static final int CHUNK_SIZE_CHARS = 500;

    private final DocumentMapper docMapper;
    private final ChunkRepository chunkRepo;
    private final KnowledgeBaseService kbService;
    private final EmbeddingClient embeddingClient;
    private final ThreadPoolExecutor docProcessExecutor;
    private final Path uploadDir;

    public DocumentServiceImpl(DocumentMapper docMapper,
                               ChunkRepository chunkRepo,
                               KnowledgeBaseService kbService,
                               EmbeddingClient embeddingClient,
                               @Qualifier("docProcessExecutor") ThreadPoolExecutor docProcessExecutor,
                               @Value("${hify.knowledge.upload-dir:./upload}") String uploadDirPath) {
        this.docMapper = docMapper;
        this.chunkRepo = chunkRepo;
        this.kbService = kbService;
        this.embeddingClient = embeddingClient;
        this.docProcessExecutor = docProcessExecutor;
        this.uploadDir = Paths.get(uploadDirPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
            log.info("文档上传目录: {}", this.uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("无法创建上传目录: " + this.uploadDir, e);
        }
    }

    // ================================================================
    // 上传
    // ================================================================

    @Override
    // 不加 @Transactional：单条 insert 本身是原子的；async 线程需要立刻读到已提交行
    public DocumentResp upload(Long kbId, String filename, byte[] content) {
        // 0) 校验知识库存在
        kbService.getById(kbId);

        // 1) 校验文件类型
        String ext = extractExt(filename);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "不支持的文件类型: " + ext + "，仅接受 " + ALLOWED_EXT);
        }

        // 2) 校验文件大小
        if (content.length > MAX_SIZE) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "文件过大: " + (content.length / 1024 / 1024) + " MB，最大 10 MB");
        }

        // 3) 落盘（时间戳防重名）
        String storedName = System.currentTimeMillis() + "_" + filename;
        Path target = uploadDir.resolve(storedName);
        try {
            Files.copy(new java.io.ByteArrayInputStream(content), target,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("文件保存失败", e);
        }

        // 4) 写 MySQL 记录
        Document doc = new Document();
        doc.setKnowledgeBaseId(kbId);
        doc.setName(filename);
        doc.setFileType(ext);
        doc.setFileSize((long) content.length);
        doc.setStatus("PENDING");
        docMapper.insert(doc);

        // 5) 提交异步任务
        final Long docId = doc.getId();
        docProcessExecutor.execute(() -> processAsync(docId, kbId, target));

        log.info("文档上传 id={} kbId={} name={} size={}",
                docId, kbId, filename, content.length);
        return DocumentResp.from(doc);
    }

    // ================================================================
    // 异步处理（运行在 docProcessExecutor 线程）
    // ================================================================

    private void processAsync(Long docId, Long kbId, Path filePath) {
        log.info("开始异步处理文档 id={}", docId);
        try {
            // ① 标记 PROCESSING
            updateStatus(docId, "PROCESSING", null);

            // ② 读取文件全文
            String text = Files.readString(filePath);

            // ③ 切块（简单按段落 + 长度分块）
            List<String> chunks = splitChunks(text, CHUNK_SIZE_CHARS);

            // ④ 向量化（调 EmbeddingClient，批量；与对话检索侧用同一模型/维度）
            List<float[]> vectors = embeddingClient.embedBatch(chunks);
            List<Object[]> rows = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                String chunk = chunks.get(i);
                // 列顺序与 ChunkRepository.batchInsert 一致：documentId, kbId, chunkIndex, content, embedding, tokenCount
                rows.add(new Object[]{docId, kbId, i, chunk, vectors.get(i), estimateTokens(chunk)});
            }
            chunkRepo.batchInsert(rows);

            // ⑤ 更新 chunk_count + 状态 → DONE（直接 UPDATE，不 SELECT）
            {
                var wrapper = new LambdaQueryWrapper<Document>().eq(Document::getId, docId);
                Document patch = new Document();
                patch.setChunkCount(chunks.size());
                patch.setStatus("DONE");
                docMapper.update(patch, wrapper);
            }

            log.info("文档处理完成 id={} chunks={}", docId, chunks.size());
        } catch (Exception e) {
            log.error("文档处理失败 id={}", docId, e);
            updateStatus(docId, "FAILED", e.getMessage());
        } finally {
            // 异步处理完可清理临时文件（保留原文件用于重试的场景可以不移除）
        }
    }

    // ================================================================
    // 查询
    // ================================================================

    @Override
    public List<DocumentResp> listByKb(Long kbId, int page, int size) {
        // 隐式校验知识库存在
        kbService.getById(kbId);
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<Document>()
                .eq(Document::getKnowledgeBaseId, kbId)
                .orderByDesc(Document::getCreatedAt);
        Page<Document> result = docMapper.selectPage(new Page<>(page, size), wrapper);
        return result.getRecords().stream().map(DocumentResp::from).toList();
    }

    @Override
    public DocumentResp getById(Long id) {
        return DocumentResp.from(requireDoc(id));
    }

    @Override
    public List<ChunkResp> listChunks(Long documentId) {
        requireDoc(documentId); // 校验存在
        return chunkRepo.findByDocumentId(documentId).stream()
                .map(this::toResp)
                .collect(Collectors.toList());
    }

    // ================================================================
    // 删除
    // ================================================================

    @Override
    @Transactional
    public void delete(Long id) {
        Document doc = requireDoc(id);
        // MySQL 逻辑删除
        docMapper.deleteById(id);
        // PG 逻辑删除
        int n = chunkRepo.logicalDeleteByDocumentId(id);
        log.info("文档删除 id={} name={} 关联 chunk={}", id, doc.getName(), n);
    }

    // ================================================================
    // 私有辅助
    // ================================================================

    private Document requireDoc(Long id) {
        Document doc = docMapper.selectById(id);
        if (doc == null) {
            throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        return doc;
    }

    /** 直接 UPDATE 状态字段，不先 SELECT（异步线程里 requireDoc 可能因事务未提交而读不到行）。 */
    private void updateStatus(Long docId, String status, String errorMsg) {
        var wrapper = new LambdaQueryWrapper<Document>().eq(Document::getId, docId);
        Document patch = new Document();
        patch.setStatus(status);
        if (errorMsg != null) {
            if (errorMsg.length() > 500) errorMsg = errorMsg.substring(0, 500);
            patch.setErrorMessage(errorMsg);
        }
        docMapper.update(patch, wrapper);
    }

    private String extractExt(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase();
    }

    /** 简单滑动分块：按段落 + 长度切分，块间保留 50 字重叠。 */
    static List<String> splitChunks(String text, int maxChars) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) return chunks;
        text = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] paragraphs = text.split("\n\n+");
        StringBuilder buf = new StringBuilder();
        for (String para : paragraphs) {
            String trimmed = para.strip();
            if (trimmed.isEmpty()) continue;
            // 如果当前段落加上去会超限，先把当前 buf 输出
            if (buf.length() > 0 && buf.length() + trimmed.length() + 2 > maxChars) {
                chunks.add(buf.toString().strip());
                // 保留最后一段作为重叠（50 字）
                String overlap = buf.length() > 50
                        ? buf.substring(buf.length() - 50) : buf.toString();
                buf = new StringBuilder(overlap);
                if (!buf.isEmpty()) buf.append("\n\n");
            }
            if (!buf.isEmpty()) buf.append("\n\n");
            buf.append(trimmed);
            // 单段超长就强制切
            while (buf.length() > maxChars) {
                int cut = maxChars;
                chunks.add(buf.substring(0, cut).strip());
                String tail = buf.substring(cut);
                String nextOverlap = buf.substring(Math.max(0, cut - 50), cut);
                buf = new StringBuilder(nextOverlap + "\n\n" + tail.strip());
            }
        }
        if (!buf.isEmpty()) chunks.add(buf.toString().strip());
        return chunks;
    }

    /** 简单 token 估算：中文按字，英文按空格分词。 */
    static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        int cjk = 0, words = 0;
        StringBuilder w = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                cjk++;
                if (!w.isEmpty()) { words++; w.setLength(0); }
            } else if (Character.isWhitespace(c)) {
                if (!w.isEmpty()) { words++; w.setLength(0); }
            } else {
                w.append(c);
            }
        }
        if (!w.isEmpty()) words++;
        return cjk + words;
    }

    private ChunkResp toResp(ChunkRow row) {
        ChunkResp r = new ChunkResp();
        r.setId(row.getId());
        r.setDocumentId(row.getDocumentId());
        r.setChunkIndex(row.getChunkIndex());
        r.setContent(row.getContent());
        r.setTokenCount(row.getTokenCount());
        r.setCreatedAt(row.getCreatedAt());
        return r;
    }
}
