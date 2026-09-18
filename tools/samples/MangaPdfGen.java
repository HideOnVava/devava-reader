import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/**
 * Generates synthetic manga-like PDFs (scanned-page style: one JPEG per page) with original
 * drawings, for screenshots and smoke tests. Every page carries a colour marker in its
 * top-left corner whose red channel encodes the page index (10 + 5*i), so a test can verify
 * that "page i" really renders page i.
 *
 * Usage: java -cp <pdfbox jars> MangaPdfGen.java <outDir>
 */
public class MangaPdfGen {

    static final String prefix = "Sample Manga";

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : ".");
        out.mkdirs();
        // Volume 1: 40 pages, 4 chapters, one landscape spread, outline.
        generate(new File(out, prefix + " v01.pdf"), prefix + " Vol. 1", 40, 1400, 2000,
                new int[]{0, 10, 20, 30}, 15, 0.82f);
        // Volume 2: 23 pages (odd), smaller scans, no outline, no spread.
        generate(new File(out, prefix + " v02.pdf"), prefix + " Vol. 2", 23, 1200, 1700,
                null, -1, 0.75f);
        // Volume 3: tiny, 3 pages, for the edge cases of the double layout.
        generate(new File(out, prefix + " v03.pdf"), "", 3, 1000, 1500, new int[]{0}, -1, 0.8f);
    }

    static void generate(File file, String title, int pages, int w, int h, int[] chapterStarts,
                         int landscapePage, float quality) throws Exception {
        long t0 = System.nanoTime();
        try (PDDocument doc = new PDDocument()) {
            if (!title.isEmpty()) {
                PDDocumentInformation info = new PDDocumentInformation();
                info.setTitle(title);
                doc.setDocumentInformation(info);
            }
            Random rnd = new Random(42);
            for (int i = 0; i < pages; i++) {
                boolean landscape = i == landscapePage;
                int pw = landscape ? w * 2 : w;
                BufferedImage img = drawPage(i, pw, h, rnd, chapterStarts, landscape);
                PDImageXObject x = JPEGFactory.createFromImage(doc, img, quality);
                // Scanned at 200 dpi: points = pixels * 72 / 200
                float ptW = pw * 72f / 200f, ptH = h * 72f / 200f;
                PDPage page = new PDPage(new PDRectangle(ptW, ptH));
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.drawImage(x, 0, 0, ptW, ptH);
                }
            }
            if (chapterStarts != null) {
                PDDocumentOutline outline = new PDDocumentOutline();
                doc.getDocumentCatalog().setDocumentOutline(outline);
                for (int c = 0; c < chapterStarts.length; c++) {
                    PDOutlineItem item = new PDOutlineItem();
                    item.setTitle("Chapter " + (c + 1) + ": " + CHAPTER_NAMES[c % CHAPTER_NAMES.length]);
                    item.setDestination(doc.getPage(chapterStarts[c]));
                    outline.addLast(item);
                }
            }
            doc.save(file);
        }
        System.out.printf("%s: %d pages, %.1f MB, %.1fs%n", file.getName(), pages,
                file.length() / 1048576.0, (System.nanoTime() - t0) / 1e9);
    }

    static final String[] CHAPTER_NAMES = {"The Beginning", "Into the City", "The Duel", "Aftermath"};

    static BufferedImage drawPage(int index, int w, int h, Random rnd, int[] chapterStarts, boolean landscape) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        // Paper (slightly off-white, like a scan)
        g.setColor(new Color(247, 246, 242));
        g.fillRect(0, 0, w, h);

        boolean cover = index == 0;
        if (cover) {
            g.setColor(new Color(30, 30, 34));
            g.fillRect(0, 0, w, h);
            g.setColor(new Color(235, 60, 60));
            g.fillRect(w / 10, h / 8, w * 8 / 10, h / 4);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, w / 8));
            drawCentered(g, prefix.toUpperCase(), w / 2, h / 8 + h / 8 + w / 24);
            g.setFont(new Font("SansSerif", Font.PLAIN, w / 16));
            drawCentered(g, "a demo volume", w / 2, h * 3 / 4);
        } else {
            // Panels: a random grid of 2-3 rows, 1-2 columns per row, with borders and "tone"
            int margin = w / 20;
            int rows = 2 + rnd.nextInt(2);
            int y = margin;
            int rowH = (h - margin * (rows + 1)) / rows;
            g.setStroke(new BasicStroke(Math.max(3, w / 250f)));
            for (int r = 0; r < rows; r++) {
                int cols = 1 + rnd.nextInt(2);
                int x = margin;
                int colW = (w - margin * (cols + 1)) / cols;
                for (int c = 0; c < cols; c++) {
                    int tone = 150 + rnd.nextInt(90);
                    g.setColor(new Color(tone, tone, tone));
                    g.fillRect(x, y, colW, rowH);
                    // "screentone" dots
                    g.setColor(new Color(90, 90, 90));
                    for (int dy = y + 12; dy < y + rowH - 12; dy += 18) {
                        for (int dx = x + 12; dx < x + colW - 12; dx += 18) {
                            if (((dx + dy) / 18) % 3 == 0) g.fillOval(dx, dy, 5, 5);
                        }
                    }
                    // speech bubble
                    g.setColor(Color.WHITE);
                    int bw = colW / 3, bh = rowH / 5;
                    g.fillOval(x + colW / 10, y + rowH / 10, bw, bh);
                    g.setColor(Color.BLACK);
                    g.drawOval(x + colW / 10, y + rowH / 10, bw, bh);
                    g.drawRect(x, y, colW, rowH);
                    x += colW + margin;
                }
                y += rowH + margin;
            }
            // Chapter title on chapter start pages
            if (chapterStarts != null) {
                for (int c = 0; c < chapterStarts.length; c++) {
                    if (chapterStarts[c] == index) {
                        g.setColor(new Color(255, 255, 255, 230));
                        g.fillRect(w / 8, h / 2 - h / 16, w * 6 / 8, h / 8);
                        g.setColor(Color.BLACK);
                        g.setFont(new Font("Serif", Font.BOLD, w / 14));
                        drawCentered(g, "Chapter " + (c + 1), w / 2, h / 2 + w / 40);
                    }
                }
            }
            if (landscape) {
                g.setColor(new Color(0, 0, 0, 200));
                g.setFont(new Font("SansSerif", Font.BOLD, w / 20));
                drawCentered(g, "DOUBLE-PAGE SPREAD", w / 2, h / 2);
            }
        }
        // Big page number, always visible (cover shows 1 too)
        g.setColor(cover ? Color.WHITE : new Color(20, 20, 20));
        g.setFont(new Font("SansSerif", Font.BOLD, w / 5));
        drawCentered(g, String.valueOf(index + 1), w / 2, h - h / 12);
        // Colour marker: red channel encodes the index
        g.setColor(new Color(10 + 5 * index, 100, 60));
        g.fillRect(0, 0, 120, 120);
        g.dispose();
        return img;
    }

    static void drawCentered(Graphics2D g, String s, int cx, int baseline) {
        int sw = g.getFontMetrics().stringWidth(s);
        g.drawString(s, cx - sw / 2, baseline);
    }
}
