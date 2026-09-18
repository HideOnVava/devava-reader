import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Generates small EPUB 3 sample novels with original text, for screenshots and smoke tests.
 * Usage: java SampleEpubGen.java <outDir>
 */
public class SampleEpubGen {

    static final String[] PARAGRAPHS = {
        "The ferry left before the mist had lifted, and for a long while the only sound was the slow knock of the oars against the hull. Mara sat at the bow with her satchel on her knees and watched the far bank refuse to appear.",
        "She had been told that the road began where the water ended, but nobody in the village had been able to say what that meant. The old ferryman only shrugged when she asked, and pointed with his chin toward the grey nothing ahead of them.",
        "By the time the sun found them, the river had narrowed into something more like a canal, lined with willows that trailed their branches in the current. A heron lifted itself out of the reeds, unhurried, and crossed to the other side as though showing her the way.",
        "The map she carried was older than her grandmother. Its edges had gone soft as cloth, and half of the names had faded to the colour of weak tea; what remained were the shapes of hills, a line of dots that might have been a path, and a small drawing of a lantern where the path came to an end.",
        "“You will know the place when you see it,” her grandmother had said, which was the kind of thing people said when they did not know it themselves.",
        "The town at the foot of the pass was smaller than she had imagined, a single street of stone houses with their shutters painted the blue of a winter sky. A clockmaker’s sign hung over the last door, and beneath it a girl of perhaps twelve sat on the step, winding a watch with enormous concentration.",
        "“It’s for a traveller,” the girl explained without looking up. “He said he’d come back for it. That was two years ago, so I keep it wound in case he’s only late.”",
        "Rain arrived on the second day of the climb, not in drops but in a fine grey curtain that soaked through wool and leather alike. Mara walked with her head down and counted her steps in hundreds, and when she lost count she started again, because counting was easier than thinking about how far there was still to go.",
        "At the top of the pass the rain stopped as suddenly as it had begun. Below her the valley opened out like a book left face-down on a table, and along the river, small and certain, a line of lights was being lit one after another as the evening came on.",
        "She thought of the ferryman, and of the girl with the watch, and of everyone who had ever waited for someone to come back down a road. Then she shouldered her satchel and started down toward the lanterns.",
        "Nobody at the inn asked where she had come from, which she found she preferred. The innkeeper set a bowl of soup in front of her and a heel of bread beside it, and went back to arguing amiably with a man about the price of lamp oil.",
        "In the morning the valley was full of birds. She could not have named a single one of them, and it occurred to her, walking out along the towpath with the sun on her back, that this was the first time in years she had not minded not knowing something.",
    };

    static final String[][] VOLUMES = {
        {"The Lantern Road", "The Ferry at Dawn", "A Map Without Names", "The Clockmaker’s Daughter", "Rain on the High Pass", "What the River Kept", "The Long Way Home"},
        {"The Lantern Road", "The Town of Blue Shutters", "Lamp Oil and Bread", "Birds Without Names", "The Towpath", "A Letter Left Unsent", "Winter at the Inn"},
        {"The Lantern Road", "The Frozen Ferry", "Two Years Late", "The Watch Runs Down", "Thaw", "The Road Back", "Lanterns"},
    };

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : ".");
        out.mkdirs();
        for (int v = 0; v < VOLUMES.length; v++) {
            String title = VOLUMES[v][0] + ", Vol. " + (v + 1);
            File f = new File(out, title.replace(",", "") + ".epub");
            write(f, title, v, VOLUMES[v]);
            System.out.println(f.getName() + " (" + f.length() / 1024 + " KB)");
        }
    }

    static void write(File file, String title, int volume, String[] chapters) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            // mimetype: first entry, stored
            byte[] mime = "application/epub+zip".getBytes(StandardCharsets.US_ASCII);
            ZipEntry e = new ZipEntry("mimetype");
            e.setMethod(ZipEntry.STORED);
            e.setSize(mime.length);
            CRC32 crc = new CRC32();
            crc.update(mime);
            e.setCrc(crc.getValue());
            zip.putNextEntry(e);
            zip.write(mime);
            zip.closeEntry();

            add(zip, "META-INF/container.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                </container>
                """);

            StringBuilder manifest = new StringBuilder();
            StringBuilder spine = new StringBuilder();
            StringBuilder nav = new StringBuilder();
            manifest.append("<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>\n");
            manifest.append("<item id=\"css\" href=\"style.css\" media-type=\"text/css\"/>\n");
            manifest.append("<item id=\"cover-image\" href=\"cover.png\" media-type=\"image/png\" properties=\"cover-image\"/>\n");
            manifest.append("<item id=\"cover\" href=\"cover.xhtml\" media-type=\"application/xhtml+xml\"/>\n");
            spine.append("<itemref idref=\"cover\"/>\n");
            nav.append("<li><a href=\"cover.xhtml\">Cover</a></li>\n");
            for (int c = 1; c < chapters.length; c++) {
                String id = String.format("ch%02d", c);
                manifest.append("<item id=\"" + id + "\" href=\"" + id + ".xhtml\" media-type=\"application/xhtml+xml\"/>\n");
                spine.append("<itemref idref=\"" + id + "\"/>\n");
                nav.append("<li><a href=\"" + id + ".xhtml\">Chapter " + c + ": " + chapters[c] + "</a></li>\n");
                add(zip, "OEBPS/" + id + ".xhtml", chapter(c, chapters[c], volume));
            }
            add(zip, "OEBPS/content.opf", """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:identifier id="uid">urn:uuid:devava-sample-%d</dc:identifier>
                    <dc:title>%s</dc:title>
                    <dc:creator>Devava Sample Books</dc:creator>
                    <dc:language>en</dc:language>
                    <meta property="dcterms:modified">2026-09-17T00:00:00Z</meta>
                  </metadata>
                  <manifest>
                %s  </manifest>
                  <spine>
                %s  </spine>
                </package>
                """.formatted(volume + 1, title, manifest, spine));
            add(zip, "OEBPS/nav.xhtml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                <head><title>Contents</title></head>
                <body><nav epub:type="toc"><h1>Contents</h1><ol>
                %s</ol></nav></body></html>
                """.formatted(nav));
            add(zip, "OEBPS/style.css", """
                body { font-family: serif; line-height: 1.5; }
                h1 { font-size: 1.6em; margin: 1.2em 0 0.8em; text-align: center; }
                p { margin: 0 0 0.8em; text-indent: 1.4em; text-align: justify; }
                p.first { text-indent: 0; }
                .cover { text-align: center; margin: 0; }
                .cover img { max-height: 95vh; }
                """);
            add(zip, "OEBPS/cover.xhtml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml"><head><title>Cover</title>
                <link rel="stylesheet" type="text/css" href="style.css"/></head>
                <body><div class="cover"><img src="cover.png" alt="Cover"/></div></body></html>
                """);
            ZipEntry img = new ZipEntry("OEBPS/cover.png");
            zip.putNextEntry(img);
            zip.write(cover(volume));
            zip.closeEntry();
        }
    }

    static String chapter(int number, String title, int volume) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>")
          .append(title).append("</title><link rel=\"stylesheet\" type=\"text/css\" href=\"style.css\"/></head><body>\n");
        sb.append("<h1>Chapter ").append(number).append("<br/>").append(title).append("</h1>\n");
        int start = (number - 1) * 3 + volume * 2;
        int count = 14 + (number % 3) * 4;
        for (int i = 0; i < count; i++) {
            String p = PARAGRAPHS[(start + i) % PARAGRAPHS.length];
            sb.append(i == 0 ? "<p class=\"first\">" : "<p>").append(p).append("</p>\n");
        }
        sb.append("</body></html>\n");
        return sb.toString();
    }

    static byte[] cover(int volume) throws Exception {
        int w = 1000, h = 1500;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Color[] tones = {new Color(28, 42, 74), new Color(58, 36, 70), new Color(30, 62, 58)};
        g.setColor(tones[volume % tones.length]);
        g.fillRect(0, 0, w, h);
        // a lantern
        g.setColor(new Color(245, 196, 92));
        g.setStroke(new BasicStroke(10));
        g.drawRoundRect(w / 2 - 110, h / 2 - 150, 220, 300, 60, 60);
        g.fillOval(w / 2 - 60, h / 2 - 60, 120, 120);
        g.drawLine(w / 2, h / 2 - 150, w / 2, h / 2 - 260);
        g.drawArc(w / 2 - 70, h / 2 - 330, 140, 140, 0, 180);
        g.setColor(new Color(250, 244, 230));
        g.setFont(new Font("Serif", Font.BOLD, 96));
        drawCentered(g, "THE LANTERN", w / 2, 260);
        drawCentered(g, "ROAD", w / 2, 380);
        g.setFont(new Font("SansSerif", Font.PLAIN, 54));
        drawCentered(g, "Volume " + (volume + 1), w / 2, h - 260);
        g.setFont(new Font("SansSerif", Font.PLAIN, 36));
        drawCentered(g, "Devava Sample Books", w / 2, h - 150);
        g.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bytes);
        return bytes.toByteArray();
    }

    static void drawCentered(Graphics2D g, String s, int cx, int baseline) {
        int sw = g.getFontMetrics().stringWidth(s);
        g.drawString(s, cx - sw / 2, baseline);
    }

    static void add(ZipOutputStream zip, String name, String text) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.stripIndent().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
