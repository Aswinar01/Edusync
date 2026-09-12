package com.example.edusync.service.revision;

import com.example.edusync.model.DocumentLocation;
import com.example.edusync.model.DocumentRevisionItem;
import com.example.edusync.model.SentenceReviewItem;
import com.example.edusync.model.TextRectangle;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PDF revision processor utilizing Apache PDFBox 3.0.2.
 * Renders yellow visual highlights over revised content, updates text content streams with approved sentences,
 * and appends professional citation reference notes at page margins.
 * Preserves the original document bytes immutably.
 */
@Service
public class PdfRevisionProcessor implements DocumentRevisionProcessor {

    private static final Logger log = LoggerFactory.getLogger(PdfRevisionProcessor.class);

    private final PdfSentenceLocator pdfSentenceLocator;

    public PdfRevisionProcessor(PdfSentenceLocator pdfSentenceLocator) {
        this.pdfSentenceLocator = pdfSentenceLocator;
    }

    @Override
    public byte[] revise(byte[] originalBytes,
                         List<DocumentRevisionItem> approvedItems,
                         List<SentenceReviewItem> allSessionItems) throws RevisionException {
        if (originalBytes == null || originalBytes.length == 0) {
            throw new RevisionException("Cannot revise empty or null PDF bytes.");
        }

        if (approvedItems == null || approvedItems.isEmpty()) {
            log.info("Zero approved updates to apply; returning defensive copy of original PDF bytes.");
            return originalBytes.clone();
        }

        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(originalBytes))) {
            Map<Integer, DocumentLocation> locations = pdfSentenceLocator.locateAll(document, allSessionItems);

            // Group items by page
            Map<Integer, List<DocumentRevisionItem>> itemsByPage = new HashMap<>();
            for (DocumentRevisionItem item : approvedItems) {
                DocumentLocation loc = locations.get(item.getSentenceId());
                if (loc == null || loc.getLineBoxes().isEmpty()) {
                    throw new SentenceLocationException(String.format(
                            "Cannot apply revision: no coordinate location resolved for approved sentence ID %d ('%s')",
                            item.getSentenceId(), item.getOriginalSentence()));
                }

                int pageIndex = loc.getPageNumber() - 1;
                if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) {
                    throw new SentenceLocationException(String.format(
                            "Page number %d out of bounds for PDF with %d pages (sentence ID %d)",
                            loc.getPageNumber(), document.getNumberOfPages(), item.getSentenceId()));
                }

                itemsByPage.computeIfAbsent(pageIndex, k -> new ArrayList<>()).add(item);
            }

            for (Map.Entry<Integer, List<DocumentRevisionItem>> entry : itemsByPage.entrySet()) {
                int pageIndex = entry.getKey();
                List<DocumentRevisionItem> pageItems = entry.getValue();
                PDPage page = document.getPage(pageIndex);

                // 1. Update text tokens in the page content stream
                try {
                    PDFStreamParser parser = new PDFStreamParser(page);
                    List<Object> tokens = parser.parse();
                    List<Object> updatedTokens = new ArrayList<>(tokens.size());

                    for (Object token : tokens) {
                        if (token instanceof COSString cosStr) {
                            String str = cosStr.getString();
                            boolean replaced = false;
                            for (DocumentRevisionItem item : pageItems) {
                                if (str.contains(item.getOriginalSentence())) {
                                    str = str.replace(item.getOriginalSentence(), item.getApprovedSentence());
                                    replaced = true;
                                }
                            }
                            if (replaced) {
                                updatedTokens.add(new COSString(str));
                            } else {
                                updatedTokens.add(token);
                            }
                        } else if (token instanceof COSArray cosArr) {
                            COSArray updatedArr = new COSArray();
                            for (int i = 0; i < cosArr.size(); i++) {
                                COSBase elem = cosArr.get(i);
                                if (elem instanceof COSString cosStr) {
                                    String str = cosStr.getString();
                                    boolean replaced = false;
                                    for (DocumentRevisionItem item : pageItems) {
                                        if (str.contains(item.getOriginalSentence())) {
                                            str = str.replace(item.getOriginalSentence(), item.getApprovedSentence());
                                            replaced = true;
                                        }
                                    }
                                    if (replaced) {
                                        updatedArr.add(new COSString(str));
                                    } else {
                                        updatedArr.add(elem);
                                    }
                                } else {
                                    updatedArr.add(elem);
                                }
                            }
                            updatedTokens.add(updatedArr);
                        } else {
                            updatedTokens.add(token);
                        }
                    }

                    PDStream updatedStream = new PDStream(document);
                    try (OutputStream out = updatedStream.createOutputStream()) {
                        ContentStreamWriter writer = new ContentStreamWriter(out);
                        writer.writeTokens(updatedTokens);
                    }
                    page.setContents(updatedStream);
                } catch (Exception ex) {
                    log.warn("Direct stream token replacement on page {}: {}. Continuing with highlight and footnote.",
                            pageIndex, ex.getMessage());
                }

                // 2. Draw yellow highlight boxes under the text (AppendMode.PREPEND)
                try (PDPageContentStream cs = new PDPageContentStream(document, page, AppendMode.PREPEND, true, true)) {
                    cs.setNonStrokingColor(1.0f, 0.95f, 0.2f); // Bright yellow highlight
                    for (DocumentRevisionItem item : pageItems) {
                        DocumentLocation loc = locations.get(item.getSentenceId());
                        for (TextRectangle box : loc.getLineBoxes()) {
                            cs.addRect(box.getX() - 2, box.getY() - 2, box.getWidth() + 4, box.getHeight() + 4);
                        }
                    }
                    cs.fill();
                }

                // 3. Render margin footnote citations (AppendMode.APPEND)
                try (PDPageContentStream cs = new PDPageContentStream(document, page, AppendMode.APPEND, true, true)) {
                    int fnIndex = 0;
                    for (DocumentRevisionItem item : pageItems) {
                        DocumentLocation loc = locations.get(item.getSentenceId());
                        float footnoteY = 30f + (fnIndex++ * 14f);
                        String citeNote = item.getCitationNote() != null && !item.getCitationNote().isBlank()
                                ? item.getCitationNote() : "Verified update";
                        String citation = String.format("[Update #%d] %s", item.getSentenceId(), citeNote);
                        if (item.getOfficialReferenceUrl() != null && !item.getOfficialReferenceUrl().isBlank()) {
                            citation += " | Ref: " + item.getOfficialReferenceUrl();
                        }

                        cs.beginText();
                        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE), 8);
                        cs.setNonStrokingColor(0.2f, 0.2f, 0.2f); // Professional dark gray
                        cs.newLineAtOffset(45, footnoteY);
                        cs.showText(truncateSafe(citation, 110));
                        cs.endText();

                        log.info("Applied revision for sentence ID {} on PDF page {} with {} highlight box(es).",
                                item.getSentenceId(), loc.getPageNumber(), loc.getLineBoxes().size());
                    }
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        } catch (SentenceLocationException sle) {
            throw sle;
        } catch (Exception ex) {
            throw new RevisionException("Failed to generate revised PDF document: " + ex.getMessage(), ex);
        }
    }

    private String truncateSafe(String text, int maxChars) {
        if (text == null) return "";
        // Clean characters that cannot be encoded in standard WinAnsi / Type 1 Helvetica
        String cleaned = text.replaceAll("[^\\x20-\\x7E]", " ");
        if (cleaned.length() <= maxChars) {
            return cleaned;
        }
        return cleaned.substring(0, maxChars - 3) + "...";
    }
}
