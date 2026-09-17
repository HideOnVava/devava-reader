package com.devavaxp.reader.epub;

import com.devavaxp.reader.epub.EpubBook.SpineItem;
import com.devavaxp.reader.epub.EpubBook.TocEntry;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads the structure of an already extracted EPUB: it locates the OPF package document
 * through {@code META-INF/container.xml}, takes the reading order from the {@code spine}
 * and builds the table of contents from the {@code nav} document (EPUB 3) or from
 * {@code toc.ncx} (EPUB 2).
 * <p>
 * Parsing is tolerant: whatever is missing or malformed degrades to the best available
 * alternative (for example, listing the HTML files by name).
 */
public final class EpubParser {

    private static final String EPUB_NS = "http://www.idpf.org/2007/ops";
    private static final Pattern DOCTYPE = Pattern.compile("(?is)<!DOCTYPE[^\\[>]*(\\[[^\\]]*\\])?[^>]*>");
    private static final Pattern NAMED_ENTITY = Pattern.compile("&([A-Za-z][A-Za-z0-9]*);");
    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(?:[xX]([0-9A-Fa-f]{1,6})|([0-9]{1,7}));");
    private static final Pattern NAV_TOC = Pattern.compile("(?is)<nav\\b[^>]*epub:type\\s*=\\s*[\"'][^\"']*toc[^\"']*[\"'][^>]*>(.*?)</nav>");
    private static final Pattern LINK = Pattern.compile("(?is)<a\\b[^>]*href\\s*=\\s*[\"']([^\"']*)[\"'][^>]*>(.*?)</a>");
    private static final Pattern TAG = Pattern.compile("(?s)<[^>]+>");
    private static final Map<String, String> ENTITIES = createEntityMap();

    private EpubParser() {
    }

    /** Parses the root folder of an extracted EPUB. Malformed content never throws. */
    public static EpubBook parse(Path root) throws IOException {
        Path absoluteRoot = root.toAbsolutePath().normalize();
        Optional<Path> opf = locateOpf(absoluteRoot);
        if (opf.isEmpty()) {
            return booksByFileName(absoluteRoot);
        }
        try {
            return parseOpf(opf.get());
        } catch (RuntimeException e) {
            System.err.println("Unreadable OPF (" + e.getMessage() + "); falling back to file-name order.");
            return booksByFileName(absoluteRoot);
        }
    }

    // ------------------------------------------------------------------
    // Locating the OPF
    // ------------------------------------------------------------------

    static Optional<Path> locateOpf(Path root) throws IOException {
        Path container = root.resolve("META-INF").resolve("container.xml");
        if (Files.isRegularFile(container)) {
            Document doc = parseXml(container);
            if (doc != null) {
                NodeList rootfiles = doc.getElementsByTagNameNS("*", "rootfile");
                String first = null;
                for (int i = 0; i < rootfiles.getLength(); i++) {
                    Element rf = (Element) rootfiles.item(i);
                    String path = rf.getAttribute("full-path");
                    if (path.isBlank()) continue;
                    if (first == null) first = path;
                    if ("application/oebps-package+xml".equalsIgnoreCase(rf.getAttribute("media-type"))) {
                        first = path;
                        break;
                    }
                }
                if (first != null) {
                    Path candidate = resolveSafely(root, decode(first));
                    if (candidate != null && Files.isRegularFile(candidate)) return Optional.of(candidate);
                }
            }
        }
        // No valid container.xml: look for any .opf inside the package.
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".opf"))
                    .findFirst();
        }
    }

    // ------------------------------------------------------------------
    // OPF: manifest + spine
    // ------------------------------------------------------------------

    private static EpubBook parseOpf(Path opf) throws IOException {
        Document doc = parseXml(opf);
        if (doc == null) {
            throw new IllegalStateException("could not parse " + opf.getFileName());
        }
        Path opfDir = opf.getParent();

        String title = "";
        NodeList titles = doc.getElementsByTagNameNS("*", "title");
        for (int i = 0; i < titles.getLength(); i++) {
            String t = normalizeText(titles.item(i).getTextContent());
            if (!t.isEmpty()) {
                title = t;
                break;
            }
        }

        record ManifestItem(String href, String mediaType, String properties) {
        }
        Map<String, ManifestItem> manifest = new LinkedHashMap<>();
        NodeList items = doc.getElementsByTagNameNS("*", "item");
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            String id = item.getAttribute("id");
            String href = item.getAttribute("href");
            if (id.isEmpty() || href.isEmpty()) continue;
            manifest.put(id, new ManifestItem(href, item.getAttribute("media-type"), item.getAttribute("properties")));
        }

        List<SpineItem> spine = new ArrayList<>();
        Map<Path, Integer> indexByFile = new HashMap<>();
        String ncxId = null;
        NodeList spines = doc.getElementsByTagNameNS("*", "spine");
        if (spines.getLength() > 0) {
            Element spineEl = (Element) spines.item(0);
            ncxId = spineEl.getAttribute("toc");
            NodeList itemrefs = spineEl.getElementsByTagNameNS("*", "itemref");
            for (int i = 0; i < itemrefs.getLength(); i++) {
                Element itemref = (Element) itemrefs.item(i);
                ManifestItem item = manifest.get(itemref.getAttribute("idref"));
                if (item == null) continue;
                Path file = resolveSafely(opfDir, decode(item.href()));
                if (file == null || !Files.isRegularFile(file)) continue;
                if (!isReadableDocument(item.mediaType(), file)) continue;
                if (indexByFile.containsKey(file)) continue; // duplicated spine entries
                boolean linear = !"no".equalsIgnoreCase(itemref.getAttribute("linear"));
                indexByFile.put(file, spine.size());
                spine.add(new SpineItem(file, Files.size(file), linear));
            }
        }
        if (spine.isEmpty()) {
            throw new IllegalStateException("the spine has no readable documents");
        }

        // Table of contents: nav first (EPUB 3), then NCX (EPUB 2).
        List<TocEntry> toc = new ArrayList<>();
        for (Map.Entry<String, ManifestItem> e : manifest.entrySet()) {
            String props = e.getValue().properties() == null ? "" : e.getValue().properties();
            if (hasNavProperty(props)) {
                Path nav = resolveSafely(opfDir, decode(e.getValue().href()));
                if (nav != null && Files.isRegularFile(nav)) {
                    toc = readNav(nav, indexByFile);
                    if (!toc.isEmpty()) break;
                }
            }
        }
        if (toc.isEmpty()) {
            ManifestItem ncxItem = ncxId == null || ncxId.isEmpty() ? null : manifest.get(ncxId);
            if (ncxItem == null) {
                ncxItem = manifest.values().stream()
                        .filter(m -> "application/x-dtbncx+xml".equalsIgnoreCase(m.mediaType())
                                || m.href().toLowerCase(Locale.ROOT).endsWith(".ncx"))
                        .findFirst().orElse(null);
            }
            if (ncxItem != null) {
                Path ncx = resolveSafely(opfDir, decode(ncxItem.href()));
                if (ncx != null && Files.isRegularFile(ncx)) {
                    toc = readNcx(ncx, indexByFile);
                }
            }
        }
        return new EpubBook(title, spine, toc);
    }

    private static boolean hasNavProperty(String properties) {
        for (String p : properties.trim().split("\\s+")) {
            if (p.equalsIgnoreCase("nav")) return true;
        }
        return false;
    }

    private static boolean isReadableDocument(String mediaType, Path file) {
        String mt = mediaType == null ? "" : mediaType.toLowerCase(Locale.ROOT);
        if (mt.contains("xhtml") || mt.contains("html") || mt.contains("svg")) return true;
        if (!mt.isEmpty()) return false;
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".xhtml") || name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".xml");
    }

    // ------------------------------------------------------------------
    // EPUB 3 table of contents (nav)
    // ------------------------------------------------------------------

    static List<TocEntry> readNav(Path nav, Map<Path, Integer> indexByFile) {
        List<TocEntry> entries = new ArrayList<>();
        Document doc = parseXml(nav);
        Path dir = nav.getParent();
        if (doc != null) {
            Element tocNav = null;
            NodeList navs = doc.getElementsByTagNameNS("*", "nav");
            for (int i = 0; i < navs.getLength(); i++) {
                Element n = (Element) navs.item(i);
                if (epubTypeOf(n).contains("toc")) {
                    tocNav = n;
                    break;
                }
            }
            if (tocNav == null && navs.getLength() > 0) tocNav = (Element) navs.item(0);
            Element listRoot = tocNav;
            if (listRoot == null) {
                NodeList ols = doc.getElementsByTagNameNS("*", "ol");
                if (ols.getLength() > 0) listRoot = (Element) ols.item(0);
            }
            if (listRoot != null) {
                Element ol = firstChild(listRoot, "ol");
                if (ol == null && "ol".equalsIgnoreCase(listRoot.getLocalName())) ol = listRoot;
                if (ol != null) walkOl(ol, 0, dir, indexByFile, entries);
            }
        }
        if (entries.isEmpty()) {
            // Not parseable as XML (odd entities, undeclared prefixes...): plain-text extraction.
            entries = readNavAsText(nav, indexByFile);
        }
        return entries;
    }

    private static void walkOl(Element ol, int level, Path dir, Map<Path, Integer> indexByFile, List<TocEntry> out) {
        for (Element li : children(ol, "li")) {
            Element link = firstChild(li, "a");
            Element label = link != null ? link : firstChild(li, "span");
            String title = label != null ? normalizeText(label.getTextContent()) : "";
            if (link != null) {
                addEntry(out, title, link.getAttribute("href"), level, dir, indexByFile);
            }
            Element sub = firstChild(li, "ol");
            if (sub != null) walkOl(sub, level + 1, dir, indexByFile, out);
        }
    }

    private static List<TocEntry> readNavAsText(Path nav, Map<Path, Integer> indexByFile) {
        List<TocEntry> entries = new ArrayList<>();
        String text;
        try {
            text = readText(nav);
        } catch (IOException e) {
            return entries;
        }
        Matcher m = NAV_TOC.matcher(text);
        String region = m.find() ? m.group(1) : text;
        Matcher links = LINK.matcher(region);
        while (links.find()) {
            String href = unescapeHtml(links.group(1));
            String title = normalizeText(unescapeHtml(TAG.matcher(links.group(2)).replaceAll(" ")));
            addEntry(entries, title, href, 0, nav.getParent(), indexByFile);
        }
        return entries;
    }

    // ------------------------------------------------------------------
    // EPUB 2 table of contents (NCX)
    // ------------------------------------------------------------------

    static List<TocEntry> readNcx(Path ncx, Map<Path, Integer> indexByFile) {
        List<TocEntry> entries = new ArrayList<>();
        Document doc = parseXml(ncx);
        if (doc == null) return entries;
        NodeList maps = doc.getElementsByTagNameNS("*", "navMap");
        if (maps.getLength() == 0) return entries;
        for (Element point : children((Element) maps.item(0), "navPoint")) {
            walkNavPoint(point, 0, ncx.getParent(), indexByFile, entries);
        }
        return entries;
    }

    private static void walkNavPoint(Element point, int level, Path dir, Map<Path, Integer> indexByFile,
                                     List<TocEntry> out) {
        String title = "";
        Element label = firstChild(point, "navLabel");
        if (label != null) {
            Element text = firstChild(label, "text");
            title = normalizeText(text != null ? text.getTextContent() : label.getTextContent());
        }
        Element content = firstChild(point, "content");
        if (content != null) {
            addEntry(out, title, content.getAttribute("src"), level, dir, indexByFile);
        }
        for (Element child : children(point, "navPoint")) {
            walkNavPoint(child, level + 1, dir, indexByFile, out);
        }
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    private static void addEntry(List<TocEntry> out, String title, String href, int level, Path dir,
                                 Map<Path, Integer> indexByFile) {
        if (href == null || href.isBlank()) return;
        String fragment = null;
        String path = href.trim();
        int hash = path.indexOf('#');
        if (hash >= 0) {
            fragment = decode(path.substring(hash + 1));
            path = path.substring(0, hash);
        }
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        Path file = path.isEmpty() ? null : resolveSafely(dir, decode(path));
        Integer index = file == null ? null : indexByFile.get(file);
        if (index == null) return; // points outside the reading order: ignored
        out.add(new TocEntry(title.isEmpty() ? "Untitled" : title, index, fragment, level));
    }

    /** Fallback without an OPF: every HTML document sorted by path. */
    private static EpubBook booksByFileName(Path root) throws IOException {
        List<SpineItem> spine = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> htmlFiles = paths.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.endsWith(".xhtml") || n.endsWith(".html") || n.endsWith(".htm");
                    })
                    .sorted()
                    .toList();
            for (Path p : htmlFiles) {
                spine.add(new SpineItem(p.toAbsolutePath().normalize(), Files.size(p), true));
            }
        }
        return new EpubBook("", spine, new ArrayList<>());
    }

    /** Resolves a relative path inside the package; returns null if it is invalid or escapes the base. */
    static Path resolveSafely(Path base, String relative) {
        if (base == null || relative == null) return null;
        String clean = relative.replace('\\', '/');
        while (clean.startsWith("/")) clean = clean.substring(1);
        if (clean.isEmpty()) return null;
        try {
            return base.resolve(clean).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /**
     * Decodes {@code %XX} sequences (UTF-8). Unlike URLDecoder it does not turn {@code +}
     * into a space, because inside file paths the plus sign is an ordinary character.
     */
    public static String decode(String s) {
        if (s == null || s.indexOf('%') < 0) return s;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length()) {
                int hi = Character.digit(s.charAt(i + 1), 16);
                int lo = Character.digit(s.charAt(i + 2), 16);
                if (hi >= 0 && lo >= 0) {
                    bytes.write((hi << 4) | lo);
                    i += 2;
                    continue;
                }
            }
            byte[] utf8 = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
            bytes.write(utf8, 0, utf8.length);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static String epubTypeOf(Element e) {
        String value = e.getAttributeNS(EPUB_NS, "type");
        if (value == null || value.isEmpty()) {
            NamedNodeMap attrs = e.getAttributes();
            for (int i = 0; i < attrs.getLength(); i++) {
                Node a = attrs.item(i);
                String name = a.getNodeName();
                if (("type".equals(a.getLocalName()) && name.contains(":")) || "epub:type".equals(name)) {
                    value = a.getNodeValue();
                    break;
                }
            }
        }
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static Element firstChild(Element parent, String localName) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE && localName.equalsIgnoreCase(localNameOf(n))) {
                return (Element) n;
            }
        }
        return null;
    }

    private static List<Element> children(Element parent, String localName) {
        List<Element> list = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE && localName.equalsIgnoreCase(localNameOf(n))) {
                list.add((Element) n);
            }
        }
        return list;
    }

    private static String localNameOf(Node n) {
        String local = n.getLocalName();
        if (local != null) return local;
        String name = n.getNodeName();
        int p = name.indexOf(':');
        return p >= 0 ? name.substring(p + 1) : name;
    }

    static String normalizeText(String s) {
        return s == null ? "" : s.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    // ------------------------------------------------------------------
    // Tolerant, offline XML parsing
    // ------------------------------------------------------------------

    private static Document parseXml(Path file) {
        try {
            String text = sanitize(readText(file));
            DocumentBuilder builder = createBuilder();
            return builder.parse(new InputSource(new StringReader(text)));
        } catch (IOException | SAXException | ParserConfigurationException | RuntimeException e) {
            return null;
        }
    }

    private static DocumentBuilder createBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setValidating(false);
        f.setExpandEntityReferences(false);
        enable(f, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        enable(f, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        enable(f, "http://xml.org/sax/features/external-general-entities", false);
        enable(f, "http://xml.org/sax/features/external-parameter-entities", false);
        DocumentBuilder b = f.newDocumentBuilder();
        b.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        b.setErrorHandler(new ErrorHandler() {
            @Override public void warning(SAXParseException e) { }
            @Override public void error(SAXParseException e) { }
            @Override public void fatalError(SAXParseException e) throws SAXException { throw e; }
        });
        return b;
    }

    private static void enable(DocumentBuilderFactory f, String feature, boolean value) {
        try {
            f.setFeature(feature, value);
        } catch (ParserConfigurationException ignored) {
            // The implementation does not know the feature: not critical.
        }
    }

    private static String readText(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') text = text.substring(1);
        return text;
    }

    /**
     * Removes the DOCTYPE (to avoid external DTDs) and turns named HTML entities into
     * characters, since a plain XML parser does not know them.
     */
    static String sanitize(String xml) {
        String withoutDoctype = DOCTYPE.matcher(xml).replaceFirst("");
        return unescapeNamed(withoutDoctype);
    }

    private static String unescapeNamed(String text) {
        Matcher m = NAMED_ENTITY.matcher(text);
        StringBuilder sb = new StringBuilder(text.length());
        while (m.find()) {
            String name = m.group(1);
            String replacement;
            switch (name) {
                case "amp", "lt", "gt", "quot", "apos" -> replacement = m.group(0);
                default -> replacement = ENTITIES.getOrDefault(name, "");
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Full unescaping for text extracted without an XML parser: named, XML and numeric entities. */
    static String unescapeHtml(String text) {
        String withNames = unescapeNamed(text);
        Matcher m = NUMERIC_ENTITY.matcher(withNames);
        StringBuilder sb = new StringBuilder(withNames.length());
        while (m.find()) {
            String replacement;
            try {
                int code = m.group(1) != null ? Integer.parseInt(m.group(1), 16) : Integer.parseInt(m.group(2));
                replacement = Character.isValidCodePoint(code) ? new String(Character.toChars(code)) : "";
            } catch (NumberFormatException e) {
                replacement = "";
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString()
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&amp;", "&");
    }

    private static Map<String, String> createEntityMap() {
        Map<String, String> m = new HashMap<>();
        m.put("nbsp", "\u00A0"); m.put("shy", "\u00AD"); m.put("copy", "©"); m.put("reg", "®"); m.put("trade", "™");
        m.put("mdash", "—"); m.put("ndash", "–"); m.put("hellip", "…"); m.put("laquo", "«"); m.put("raquo", "»");
        m.put("ldquo", "“"); m.put("rdquo", "”"); m.put("lsquo", "‘"); m.put("rsquo", "’"); m.put("bdquo", "„");
        m.put("iexcl", "¡"); m.put("iquest", "¿"); m.put("middot", "·"); m.put("bull", "•"); m.put("deg", "°");
        m.put("euro", "€"); m.put("pound", "£"); m.put("yen", "¥"); m.put("cent", "¢"); m.put("sect", "§");
        m.put("para", "¶"); m.put("times", "×"); m.put("divide", "÷"); m.put("plusmn", "±"); m.put("frac12", "½");
        m.put("ensp", "\u2002"); m.put("emsp", "\u2003"); m.put("thinsp", "\u2009"); m.put("zwnj", "\u200C"); m.put("zwj", "\u200D");
        m.put("lrm", "\u200E"); m.put("rlm", "\u200F"); m.put("dagger", "†"); m.put("Dagger", "‡"); m.put("permil", "‰");
        m.put("prime", "′"); m.put("Prime", "″"); m.put("lsaquo", "‹"); m.put("rsaquo", "›"); m.put("oline", "‾");
        m.put("larr", "←"); m.put("rarr", "→"); m.put("uarr", "↑"); m.put("darr", "↓"); m.put("harr", "↔");
        m.put("hearts", "♥"); m.put("diams", "♦"); m.put("clubs", "♣"); m.put("spades", "♠"); m.put("star", "☆");
        m.put("aacute", "á"); m.put("eacute", "é"); m.put("iacute", "í"); m.put("oacute", "ó"); m.put("uacute", "ú");
        m.put("Aacute", "Á"); m.put("Eacute", "É"); m.put("Iacute", "Í"); m.put("Oacute", "Ó"); m.put("Uacute", "Ú");
        m.put("ntilde", "ñ"); m.put("Ntilde", "Ñ"); m.put("uuml", "ü"); m.put("Uuml", "Ü"); m.put("ccedil", "ç");
        m.put("Ccedil", "Ç"); m.put("agrave", "à"); m.put("egrave", "è"); m.put("igrave", "ì"); m.put("ograve", "ò");
        m.put("ugrave", "ù"); m.put("acirc", "â"); m.put("ecirc", "ê"); m.put("icirc", "î"); m.put("ocirc", "ô");
        m.put("ucirc", "û"); m.put("auml", "ä"); m.put("euml", "ë"); m.put("iuml", "ï"); m.put("ouml", "ö");
        m.put("Auml", "Ä"); m.put("Ouml", "Ö"); m.put("szlig", "ß"); m.put("aelig", "æ"); m.put("AElig", "Æ");
        m.put("oslash", "ø"); m.put("Oslash", "Ø"); m.put("aring", "å"); m.put("Aring", "Å"); m.put("atilde", "ã");
        m.put("otilde", "õ"); m.put("ordf", "ª"); m.put("ordm", "º"); m.put("micro", "µ"); m.put("infin", "∞");
        return m;
    }
}
