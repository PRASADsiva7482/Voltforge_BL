package in.voltforge.api.ai.document;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;

/** Invoked only as a memory/time-bounded child JVM; never starts a Spring app. */
public final class DocumentPdfWorker {
    private DocumentPdfWorker() {}
    public static void main(String[] args) throws Exception {
        if(args.length!=2) throw new IOException();
        var input=Path.of(args[0]);
        if(Files.size(input)>DocumentExtractor.MAX_BYTES) throw new IOException();
        var pages=new ArrayList<String>();
        try(var pdf=Loader.loadPDF(input.toFile())) {
            if(pdf.isEncrypted()||!pdf.getCurrentAccessPermission().canExtractContent()||pdf.getNumberOfPages()>DocumentExtractor.MAX_PAGES) throw new IOException();
            var stripper=new PDFTextStripper(); int remaining=DocumentExtractor.MAX_CHARACTERS;
            for(int page=1;page<=pdf.getNumberOfPages();page++) {
                stripper.setStartPage(page); stripper.setEndPage(page);
                var writer=new LimitedWriter(remaining); stripper.writeText(pdf,writer);
                pages.add(writer.toString()); remaining-=writer.length();
            }
        }
        try(var out=new DataOutputStream(Files.newOutputStream(Path.of(args[1])))) {
            out.writeInt(pages.size());
            for(String page:pages) { byte[] text=page.getBytes(StandardCharsets.UTF_8); out.writeInt(text.length); out.write(text); }
        }
    }
    private static class LimitedWriter extends Writer {
        private final StringBuilder text=new StringBuilder(); private final int maximum;
        LimitedWriter(int maximum) { this.maximum=maximum; }
        @Override public void write(char[] chars,int offset,int length) throws IOException {
            if(length>maximum-text.length()) throw new IOException(); text.append(chars,offset,length);
        }
        int length() { return text.length(); }
        @Override public void flush() {}
        @Override public void close() {}
        @Override public String toString() { return text.toString(); }
    }
}
