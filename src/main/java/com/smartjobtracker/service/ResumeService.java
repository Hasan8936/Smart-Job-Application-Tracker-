package com.smartjobtracker.service;

import com.smartjobtracker.model.Resume;
import com.smartjobtracker.model.User;
import com.smartjobtracker.repository.ResumeRepository;
import com.smartjobtracker.repository.UserRepository;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class ResumeService {

    private final ResumeRepository resumeRepository;
    private final UserRepository userRepository;

    public ResumeService(ResumeRepository resumeRepository, UserRepository userRepository) {
        this.resumeRepository = resumeRepository;
        this.userRepository = userRepository;
    }

    public Resume upload(Long userId, MultipartFile file) throws Exception {
        // reject very large files
        long maxSize = 10 * 1024 * 1024; // 10 MB
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("File too large (max 10MB)");
        }

        String text = extractText(file);

        // If PDF extraction returns very little text, it may be a scanned PDF.
        if (text == null) text = "";
        if (text.trim().length() < 50 && file.getOriginalFilename() != null && file.getOriginalFilename().toLowerCase().endsWith(".pdf")) {
            // mark as likely-scanned; keep empty extractedText for now
            text = "";
        }

        Resume r = new Resume();
        r.setUserId(userId);
        r.setFileName(file.getOriginalFilename());
        r.setExtractedText(text);
        return resumeRepository.save(r);
    }

    private String extractText(MultipartFile file) throws Exception {
        String fname = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        try (InputStream is = file.getInputStream()) {
            if (fname.endsWith(".pdf")) {
                try (PDDocument doc = PDDocument.load(is)) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    return stripper.getText(doc);
                }
            } else if (fname.endsWith(".docx")) {
                try (XWPFDocument doc = new XWPFDocument(is)) {
                    return doc.getParagraphs().stream().map(p -> p.getText()).reduce((a,b)->a+"\n"+b).orElse("");
                }
            } else if (fname.endsWith(".doc")) {
                // legacy .doc handling using HWPF
                try (org.apache.poi.hwpf.HWPFDocument hwpf = new org.apache.poi.hwpf.HWPFDocument(is)) {
                    org.apache.poi.hwpf.extractor.WordExtractor extractor = new org.apache.poi.hwpf.extractor.WordExtractor(hwpf);
                    String[] paras = extractor.getParagraphText();
                    return String.join("\n", paras);
                }
            } else {
                // fallback: read raw bytes as text
                byte[] all = is.readAllBytes();
                return new String(all, StandardCharsets.UTF_8);
            }
        }
    }

    public List<Resume> listByUser(Long userId) {
        return resumeRepository.findByUserId(userId);
    }
}
