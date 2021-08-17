import java.io.*;
import java.time.Month;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DocumentFetcher {
    private static HashMap<Integer, String> internalIdToDocnoMap = new HashMap<>();
    private static HashMap<String, Integer> docnoToInternalIdMap = new HashMap<>();
    private static HashMap<Integer, String[]> metadataDict = new HashMap<>();
    private static final String DOCNO = "docno";

    public static void main(String[] args) {
        validateInputs(args);

        final String STORE_PATH = args[0];
        final String identifierType = args[1];
        final String identifier = args[2];

        initializeMappingDict(STORE_PATH);
        initializeMetadataDict(STORE_PATH);

        String[] metadata;
        ArrayList<String> document;

        if (identifierType.equals(DOCNO)) {
            int internalId = docnoToInternalIdMap.get(identifier);
            metadata = metadataDict.get(internalId);
            document = getRawDocument(STORE_PATH, identifier, metadata[1]);
            outputDocument(identifier, internalId, metadata[1], metadata[2], document);
        } else {
            int id = Integer.parseInt(identifier);
            String docno = internalIdToDocnoMap.get(id);
            metadata = metadataDict.get(id);
            document = getRawDocument(STORE_PATH, docno, metadata[1]);
            outputDocument(docno, id, metadata[1], metadata[2], document);
        }
    }

    private static void initializeMappingDict(String storePath) {
        // Reads mapping file and creates dictionaries internal id -> DOCNO and DOCNO -> internal id
        try {
            File mappingFile = new File(storePath + "/IDMapping.txt");
            BufferedReader mappingReader = new BufferedReader(new FileReader(mappingFile));

            String mappingLine = mappingReader.readLine();

            while (mappingLine != null) {
                String[] mappings = mappingLine.split(" ");
                int internalId = Integer.parseInt(mappings[0]);
                String docno = mappings[1];

                internalIdToDocnoMap.put(internalId, docno);
                docnoToInternalIdMap.put(docno, internalId);

                mappingLine = mappingReader.readLine();
            }
            System.out.println("Created internalIdToDocnoMap with " + internalIdToDocnoMap.size() + " document keys");
            System.out.println("Created docnoToInternalIdMap with " + docnoToInternalIdMap.size() + " document keys");
        } catch (IOException e) {
            Logger.getLogger("DocumentFetcher").log(Level.SEVERE, e.toString());
        }
    }

    private static void initializeMetadataDict(String storePath) {
        // Reads metadata file and creates dictionary internal id -> metadata

        try {
            File mappingFile = new File(storePath + "/metadata.txt");
            BufferedReader mappingReader = new BufferedReader(new FileReader(mappingFile));
            String mappingLine = mappingReader.readLine();

            while (mappingLine != null) {
                int id = Integer.parseInt(mappingLine);
                String docno = mappingReader.readLine();
                String date = mappingReader.readLine();
                String headline = mappingReader.readLine();
                mappingLine = mappingReader.readLine();

                // value of dictionary is a string array of size three
                metadataDict.put(id, new String[3]);
                metadataDict.get(id)[0] = docno;
                metadataDict.get(id)[1] = date;
                metadataDict.get(id)[2] = headline;
            }
            System.out.println("Created metadataDict with " + metadataDict.size() + " document keys");
        } catch (IOException e) {
            Logger.getLogger("DocumentFetcher").log(Level.SEVERE, e.toString());
        }
    }

    private static ArrayList<String> getRawDocument(String storePath, String docno, String date) {
        // fetches the raw document stored in file system to be outputted

        ArrayList<String> rawDocument = new ArrayList<>();
        try {
            String month = date.substring(0, 2);
            String day = date.substring(2, 4);
            String year = date.substring(4, 6);
            File rawDocumentFile = new File(storePath + "/" + year + "/" + month + "/" + day + "/" + docno + ".txt");
            BufferedReader rawDocumentReader = new BufferedReader(new FileReader(rawDocumentFile));
            String documentLine = rawDocumentReader.readLine();

            while (documentLine != null) {
                rawDocument.add(documentLine);
                documentLine = rawDocumentReader.readLine();
            }
        } catch (IOException e) {
            Logger.getLogger("DocumentFetcher").log(Level.SEVERE, e.toString());
        }

        return rawDocument;
    }

    private static void validateInputs(String[] args) {
        if (args.length < 3) {
            Logger.getLogger("DocumentFetcher")
                    .log(Level.INFO,
                            "Exiting... The DocumentFetcher program takes three arguments. " +
                                    "The first argument is the location of the data storage in the file system." +
                                    "The second argument is the type of document identifier being used. The options are \"id\" or \"docno\"." +
                                    "The third argument is the \"id\" or \"docno\" itself based on the second argument provided.");
            System.exit(1);
        }
    }

    private static void outputDocument(String docno, int internalId, String date, String headline, ArrayList<String> rawDocument) {
        // nicely outputs metadata and raw document

        System.out.println("docno: " + docno);
        System.out.println("internal id: " + internalId);
        System.out.println("date: " + getFormattedDate(date));
        System.out.println("headline: " + headline);
        System.out.println("raw document: ");
        for (String line : rawDocument) {
            System.out.println(line);
        }
    }

    private static String getFormattedDate(String date) {
        String month = date.substring(0, 2);
        String day = date.substring(2, 4);
        String year = date.substring(4, 6);
        return Month.of(Integer.parseInt(month)).name() + " " + Integer.parseInt(day) + ", 19" + year;
    }
}
