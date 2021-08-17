import java.io.*;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SnippetEngine {
    private String storePath;

    SnippetEngine(String storePath) {
        this.storePath = storePath;
    }

    public void saveDocument(ArrayList<String> document, String docno, String[] date) throws IOException {
        // Saves document to file system in the following format: YY/MM/DD/DOCNO.txt

        String month = date[0];
        String day = date[1];
        String year = date[2];

        File documentPath = new File(storePath + "/" + year + "/" + month + "/" + day);
        if(!documentPath.exists()) {
            documentPath.mkdirs();
        }
        File documentFile = new File(storePath + "/" + year + "/" + month + "/" + day + "/" + docno + ".txt");
        FileOutputStream documentFOS = new FileOutputStream(documentFile);
        BufferedWriter documentBW = new BufferedWriter(new OutputStreamWriter(documentFOS));

        document.forEach((docLine) -> {
            try {
                documentBW.write(docLine);
                documentBW.newLine();
            } catch(IOException e) {
                Logger.getLogger("SnippetEngine").log(Level.SEVERE, e.toString());
            }
        });

        documentBW.close();
    }
}
