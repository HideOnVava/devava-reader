// PDFBox ships without a module descriptor, so it is an automatic module by necessity.
@SuppressWarnings("requires-automatic")
module com.devavaxp.reader {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires jdk.jsobject;        // netscape.javascript.JSObject (JavaScript <-> Java bridge of the reader)
    requires java.xml;            // DOM parsing of container.xml, OPF, nav and NCX
    requires java.desktop;        // FileSystemView (Documents folder) and BufferedImage (PDF rendering)
    requires com.google.gson;
    requires org.apache.pdfbox;      // fixed-page reader (PDF)
    requires org.apache.pdfbox.io;   // stream cache used when opening a PDF
    // PDFBox logs through commons-logging, which is an explicit module: being automatic,
    // PDFBox cannot declare the dependency itself, so it has to be pulled in here.
    requires org.apache.commons.logging;

    // JavaFX instantiates the Application subclass and the FXML controllers reflectively
    opens com.devavaxp.reader to javafx.fxml, javafx.graphics;

    // The WebView invokes the public methods of the JavaScript -> Java bridge reflectively.
    // The call goes through a trampoline class that lives in an unnamed module, so the
    // export must be unqualified (a qualified export to javafx.web is not enough).
    exports com.devavaxp.reader.bridge;

    // Gson reads the private fields of the models and of the library structure
    opens com.devavaxp.reader.model to com.google.gson;
    opens com.devavaxp.reader.data to com.google.gson;
}
