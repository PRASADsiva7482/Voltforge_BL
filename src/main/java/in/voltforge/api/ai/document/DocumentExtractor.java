package in.voltforge.api.ai.document;

import in.voltforge.api.ai.gateway.AiGatewayException;
import in.voltforge.api.common.exception.BadRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Text-only extraction. Embedded actions, links and attachments are never executed. */
@Component
public class DocumentExtractor {
    public static final int MAX_BYTES=2*1024*1024, MAX_CHARACTERS=200_000, MAX_PAGES=80;
    private final Semaphore admission=new Semaphore(1);
    public record Part(int page,String text) {}
    public record Extracted(String hash,String mediaType,int pages,List<Part> chunks) {}
    public Extracted extract(byte[] bytes,String filename) {
        if(bytes.length==0||bytes.length>MAX_BYTES) throw new BadRequestException("Upload a nonempty file no larger than 2 MiB");
        if(!admission.tryAcquire()) throw new AiGatewayException(HttpStatus.TOO_MANY_REQUESTS,"DOCUMENT_BUSY","Another document is being read. Retry shortly.",true);
        try {
            String lower=Objects.toString(filename,"").toLowerCase(Locale.ROOT);
            var pages=new ArrayList<String>();
            String type;
            if(lower.endsWith(".pdf")) {
                if(!new String(bytes,0,Math.min(5,bytes.length),StandardCharsets.US_ASCII).equals("%PDF-")) throw new IOException();
                type="application/pdf";
                pages.addAll(pdfPages(bytes));
            } else if(lower.endsWith(".txt")||lower.endsWith(".md")) {
                type="text/plain";
                String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
                if(text.length()>MAX_CHARACTERS||text.indexOf('\0')>=0) throw new BadRequestException("Use UTF-8 text with no more than 200,000 characters");
                pages.add(text.replace("\ufeff",""));
            } else throw new BadRequestException("Supported uploads are PDF, UTF-8 TXT and Markdown");
            var chunks=new ArrayList<Part>();
            for(int i=0;i<pages.size();i++) {
                String text=pages.get(i).replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ").strip();
                for(int start=0;start<text.length();) {
                    int end=Math.min(start+1600,text.length());
                    if(end<text.length()&&Character.isHighSurrogate(text.charAt(end-1))) end--;
                    String part=text.substring(start,end).strip();
                    if(!part.isBlank()) chunks.add(new Part(i+1,part));
                    if(chunks.size()>256) throw new BadRequestException("Document exceeds 256 text chunks; upload a smaller section");
                    if(end==text.length()) break;
                    start=end-160;
                }
            }
            if(chunks.isEmpty()) throw new BadRequestException("No extractable text found. Scanned PDFs require OCR, which is not supported");
            return new Extracted(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),type,pages.size(),List.copyOf(chunks));
        } catch(IOException|GeneralSecurityException e) {
            throw new BadRequestException("Could not extract this document. Use an unencrypted text PDF or valid UTF-8 text within the limits");
        } finally { admission.release(); }
    }
    private List<String> pdfPages(byte[] bytes) throws IOException {
        // A malformed/compressed PDF cannot exhaust the application JVM heap or
        // leave an uninterruptible parser thread running after the deadline.
        Path folder=Files.createTempDirectory("vf-document-");
        Path input=folder.resolve("input.pdf"), output=folder.resolve("pages.bin");
        Process worker=null;
        try {
            Files.write(input,bytes);
            String classpath=System.getProperty("java.class.path");
            var command=new ArrayList<String>(List.of(Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString(),"-Xmx128m","-XX:MaxMetaspaceSize=96m"));
            // Boot's executable archive needs its nested-jar loader. IDE/Maven
            // executions use the normal application/test classpath.
            boolean boot=DocumentExtractor.class.getResource("DocumentExtractor.class").toString().contains("BOOT-INF/classes");
            if(boot) command.add("-Dloader.main="+DocumentPdfWorker.class.getName());
            command.addAll(List.of("-cp",classpath,boot?"org.springframework.boot.loader.launch.PropertiesLauncher":DocumentPdfWorker.class.getName(),input.toString(),output.toString()));
            worker=new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if(!worker.waitFor(20,TimeUnit.SECONDS)) throw new BadRequestException("PDF extraction timed out. Upload a smaller section");
            if(worker.exitValue()!=0||!Files.isRegularFile(output)||Files.size(output)>MAX_CHARACTERS*4L+MAX_PAGES*4L+4) throw new BadRequestException("PDF extraction failed. Use an unencrypted text PDF within the page, text and memory limits");
            try(var data=new DataInputStream(Files.newInputStream(output))) {
                int count=data.readInt(), total=0;
                if(count<1||count>MAX_PAGES) throw new IOException();
                var pages=new ArrayList<String>();
                for(int i=0;i<count;i++) {
                    int size=data.readInt(); if(size<0||size>MAX_CHARACTERS*4) throw new IOException();
                    byte[] text=data.readNBytes(size); if(text.length!=size) throw new IOException();
                    String page=new String(text,StandardCharsets.UTF_8); total+=page.length();
                    if(total>MAX_CHARACTERS) throw new IOException(); pages.add(page);
                }
                return pages;
            }
        } catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new BadRequestException("Document extraction interrupted; retry the upload"); }
        finally {
            if(worker!=null&&worker.isAlive()) {
                worker.destroyForcibly();
                try { worker.waitFor(3,TimeUnit.SECONDS); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            Files.deleteIfExists(input); Files.deleteIfExists(output); Files.deleteIfExists(folder);
        }
    }
}
