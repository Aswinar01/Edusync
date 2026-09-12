package com.example.edusync.model;

import java.util.Collections;
import java.util.List;

/**
 * Structural or coordinate location of a sentence within an original document.
 * Supports both PDF (page number + line bounding boxes) and DOCX (paragraph and run spans).
 */
public class DocumentLocation {

    private final DocumentType documentType;

    // PDF-specific provenance
    private final int pageNumber; // 1-based
    private final List<TextRectangle> lineBoxes;

    // DOCX-specific provenance
    private final int paragraphIndex;
    private final int startRunIndex;
    private final int startOffsetInRun;
    private final int endRunIndex;
    private final int endOffsetInRun;

    public static DocumentLocation forPdf(int pageNumber, List<TextRectangle> lineBoxes) {
        return new DocumentLocation(DocumentType.PDF, pageNumber, lineBoxes, -1, -1, -1, -1, -1);
    }

    public static DocumentLocation forDocx(int paragraphIndex,
                                           int startRunIndex,
                                           int startOffsetInRun,
                                           int endRunIndex,
                                           int endOffsetInRun) {
        return new DocumentLocation(DocumentType.DOCX, -1, Collections.emptyList(),
                paragraphIndex, startRunIndex, startOffsetInRun, endRunIndex, endOffsetInRun);
    }

    private DocumentLocation(DocumentType documentType,
                             int pageNumber,
                             List<TextRectangle> lineBoxes,
                             int paragraphIndex,
                             int startRunIndex,
                             int startOffsetInRun,
                             int endRunIndex,
                             int endOffsetInRun) {
        this.documentType = documentType;
        this.pageNumber = pageNumber;
        this.lineBoxes = lineBoxes != null ? List.copyOf(lineBoxes) : Collections.emptyList();
        this.paragraphIndex = paragraphIndex;
        this.startRunIndex = startRunIndex;
        this.startOffsetInRun = startOffsetInRun;
        this.endRunIndex = endRunIndex;
        this.endOffsetInRun = endOffsetInRun;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public List<TextRectangle> getLineBoxes() {
        return lineBoxes;
    }

    public int getParagraphIndex() {
        return paragraphIndex;
    }

    public int getStartRunIndex() {
        return startRunIndex;
    }

    public int getStartOffsetInRun() {
        return startOffsetInRun;
    }

    public int getEndRunIndex() {
        return endRunIndex;
    }

    public int getEndOffsetInRun() {
        return endOffsetInRun;
    }

    @Override
    public String toString() {
        if (documentType == DocumentType.PDF) {
            return "DocumentLocation{type=PDF, page=" + pageNumber + ", lineBoxes=" + lineBoxes.size() + '}';
        } else {
            return "DocumentLocation{type=DOCX, paragraph=" + paragraphIndex +
                    ", startRun=" + startRunIndex + ":" + startOffsetInRun +
                    ", endRun=" + endRunIndex + ":" + endOffsetInRun + '}';
        }
    }
}
