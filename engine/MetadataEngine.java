import java.io.*;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MetadataEngine {
    private String storePath;
    private BufferedWriter mappingBufferedWriter;
    private BufferedWriter metaDataBufferedWriter;
    private static final String HEADLINE_START_TAG = "<HEADLINE>";
    private static final String HEADLINE_END_TAG = "</HEADLINE>";
    private static final String MAPPING_FILENAME = "/IDMapping.txt";
    private static final String METADATA_FILENAME = "/metadata.txt";

    MetadataEngine(String storePath) {
        this.storePath = storePath;
        initializeWriter();
    }

    public void saveMetadata(ArrayList<String> documentArray, String docno, int internalId, String[] date) throws IOException {
        String month = date[0];
        String day = date[1];
        String year = date[2];
        String formattedDate = month + day + year;
        String headline = DocumentUtils.extractTextByTag(documentArray, HEADLINE_START_TAG, HEADLINE_END_TAG);

        // Write document metadata to file
        metaDataBufferedWriter.write(String.valueOf(internalId));
        metaDataBufferedWriter.newLine();
        metaDataBufferedWriter.write(docno);
        metaDataBufferedWriter.newLine();
        metaDataBufferedWriter.write(formattedDate);
        metaDataBufferedWriter.newLine();
        metaDataBufferedWriter.write(headline);
        metaDataBufferedWriter.newLine();


        // Write document internal ID and DOCNO to file
        mappingBufferedWriter.write(internalId + " " + docno);
        mappingBufferedWriter.newLine();
    }

    public void closeWriters() {
        try {
            mappingBufferedWriter.close();
            metaDataBufferedWriter.close();
        } catch (IOException e) {
            Logger.getLogger("MetadataEngine").log(Level.SEVERE, e.toString());
        }
    }

    private void initializeWriter() {
        try {
            File mappingFile = new File(storePath + MAPPING_FILENAME);
            FileOutputStream mappingFOS = new FileOutputStream(mappingFile);
            mappingBufferedWriter = new BufferedWriter(new OutputStreamWriter(mappingFOS));

            File metaDataFile = new File(storePath + METADATA_FILENAME);
            FileOutputStream metaDataFileFOS = new FileOutputStream(metaDataFile);
            metaDataBufferedWriter = new BufferedWriter(new OutputStreamWriter(metaDataFileFOS));
        } catch (IOException e) {
            Logger.getLogger("MetadataEngine").log(Level.SEVERE, e.toString());
        }
    }
}
