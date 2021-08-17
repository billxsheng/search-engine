import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

public class IndexEngine {
    private static final String DOCNO_START_TAG = "<DOCNO>";
    private static final String DOC_START_TAG = "<DOC>";
    private static final String DOC_END_TAG = "</DOC>";
    private static final String HEADLINE_START_TAG = "<HEADLINE>";
    private static final String HEADLINE_END_TAG = "</HEADLINE>";
    private static final String GRAPHIC_START_TAG = "<GRAPHIC>";
    private static final String GRAPHIC_END_TAG = "</GRAPHIC>";
    private static final String TEXT_START_TAG = "<TEXT>";
    private static final String TEXT_END_TAG = "</TEXT>";

    public static void main(String[] args) {
        validateInputs(args);

        final String DATA_PATH = args[0];
        final String STORE_PATH = args[1];
        final boolean STEM = false;

        File storeDirectory = new File(STORE_PATH);
        storeDirectory.mkdirs();
        Logger.getLogger("IndexEngine").log(Level.INFO, "Creating storage directory at " + STORE_PATH);

        // Initialize engine instances
        SnippetEngine snippetEngine = new SnippetEngine(STORE_PATH);
        MetadataEngine metadataEngine = new MetadataEngine(STORE_PATH);

        ArrayList<Integer> documentLengths = new ArrayList<>();
        Map<String, Integer> termToIdLexicon = new HashMap<>();
        Map<Integer, String> idToTermLexicon = new HashMap<>();
        Map<Integer, ArrayList<Integer>> invertedIndex = new HashMap<>();

        try {
            long start = System.currentTimeMillis();

            InputStream fileStream = new FileInputStream(DATA_PATH);
            InputStream gzipStream = new GZIPInputStream(fileStream);
            Reader decoder = new InputStreamReader(gzipStream);
            BufferedReader bufferedReader = new BufferedReader(decoder);

            ArrayList<String> documentArray = new ArrayList<>();

            // Since every document has a DOCNO, we extract it from the document while we are streaming it
            String docno = null;
            String documentLine = bufferedReader.readLine();
            int internalId = 0;

            Logger.getLogger("IndexEngine").log(Level.INFO, "Saving documents one-by-one to FS and writing to mapping/metadata files...");

            // Stream latimes.gz one document at a time
            while (documentLine != null) {
                if (documentLine.equals(DOC_START_TAG)) {
                    // If we detect a document start tag, we clear the previous document and begin reading in the next document
                    documentArray.clear();
                } else if (documentLine.startsWith(DOCNO_START_TAG)) {
                    docno = processDocnoLine(documentLine);
                }

                documentArray.add(documentLine);

                if (documentLine.equals(DOC_END_TAG)) {
                    // At this stage, we know we have the entire document in the ArrayList and can begin to process it
                    metadataEngine.saveMetadata(documentArray, docno, internalId, getDateByDocno(docno));
                    snippetEngine.saveDocument(documentArray, docno, getDateByDocno(docno));

                    String documentText = getDocumentText(documentArray);

                    ArrayList<String> tokens;

                    if(STEM) {
                        tokens = DocumentUtils.tokenizeAndStem(documentText);
                    } else {
                        tokens = DocumentUtils.tokenize(documentText);
                    }

                    documentLengths.add(tokens.size());
                    ArrayList<Integer> tokenIds = convertTokensToIds(tokens, termToIdLexicon, idToTermLexicon);
                    Map<Integer, Integer> wordCounts = countWords(tokenIds);
                    addPostings(wordCounts, internalId, invertedIndex);
                    internalId++;
                }

                documentLine = bufferedReader.readLine();
            }

            Logger.getLogger("IndexEngine").log(Level.INFO, "Serializing lexicon dictionaries...");
            writeObjectToFS(termToIdLexicon,STORE_PATH + "/termToIdLexicon.ser");
            writeObjectToFS(idToTermLexicon,STORE_PATH + "/idToTermLexicon.ser");

            Logger.getLogger("IndexEngine").log(Level.INFO, "Serializing document lengths array...");
            writeObjectToFS(documentLengths,STORE_PATH + "/documentLengths.ser");

            Logger.getLogger("IndexEngine").log(Level.INFO, "Serializing inverted index...");
            writeObjectToFS(invertedIndex,STORE_PATH + "/invertedIndex.ser");

            metadataEngine.closeWriters();

            long finish = System.currentTimeMillis();
            long timeElapsed = finish - start;
            Logger.getLogger("IndexEngine").log(Level.SEVERE, "Time taken: " + timeElapsed + " milliseconds");


            Logger.getLogger("IndexEngine").log(Level.INFO, "Finished indexing documents and metadata/mappings! Indexed " + internalId + " documents.");
        } catch (IOException e) {
            Logger.getLogger("IndexEngine").log(Level.SEVERE, e.toString());
        }
    }

    private static void writeObjectToFS(Object objToSave, String path) {
        // Write an object to the FS at the given path

        try {
            FileOutputStream fos = new FileOutputStream(path);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(objToSave);

            oos.close();
            fos.close();
        } catch(IOException e) {
            Logger.getLogger("IndexEngine").log(Level.SEVERE, e.toString());
        }
    }

    private static String getDocumentText(ArrayList<String> documentArray) {
        // Gets the text within a document for the HEADLINE, TEXT, GRAPHIC tags

        StringBuffer documentText = new StringBuffer();

        documentText.append(DocumentUtils.extractTextByTag(documentArray, HEADLINE_START_TAG, HEADLINE_END_TAG));
        documentText.append(" ");
        documentText.append(DocumentUtils.extractTextByTag(documentArray, GRAPHIC_START_TAG, GRAPHIC_END_TAG));
        documentText.append(" ");
        documentText.append(DocumentUtils.extractTextByTag(documentArray, TEXT_START_TAG, TEXT_END_TAG));

        return documentText.toString();
    }

    private static ArrayList<Integer> convertTokensToIds(ArrayList<String> tokens, Map<String, Integer> termToIdLexicon, Map<Integer, String> idToTermLexicon) {
        // Converts tokens to token ids and updates the lexicon dictionaries in the process

        ArrayList<Integer> tokenIds = new ArrayList<>();
        for (String token : tokens) {
            if (!termToIdLexicon.containsKey(token)) {
                int id = termToIdLexicon.size();
                termToIdLexicon.put(token, id);
                idToTermLexicon.put(id, token);
            }
            tokenIds.add(termToIdLexicon.get(token));
        }
        return tokenIds;
    }

    private static Map<Integer, Integer> countWords(ArrayList<Integer> tokenIds) {
        // Given a list of tokens, count the number of occurrences for the tokens that exist

        Map<Integer, Integer> wordCounts = new HashMap<>();
        for (int id : tokenIds) {
            if (wordCounts.containsKey(id)) {
                wordCounts.put(id, wordCounts.get(id) + 1);
            } else {
                wordCounts.put(id, 1);
            }
        }

        return wordCounts;
    }

    private static void addPostings(Map<Integer, Integer> wordCounts, int internalId, Map<Integer, ArrayList<Integer>> invIndex) {
        // Update the inverted index
        // Add the document internal id/number of occurrences of the given token for the posting mapped from the token id

        for (Integer termId : wordCounts.keySet()) {
            int count = wordCounts.get(termId);
            if (!invIndex.containsKey(termId)) {
                invIndex.put(termId, new ArrayList<>());
            }
            ArrayList<Integer> postings = invIndex.get(termId);
            postings.add(internalId);
            postings.add(count);
        }
    }

    private static void validateInputs(String[] args) {
        // Validate arguments provided and exit program if errors encountered

        if (args.length < 2) {
            Logger.getLogger("IndexEngine")
                    .log(Level.INFO,
                            "Exiting... HELP: The IndexEngine program takes two arguments. " +
                                    "The first argument is the location of the data file in the file system. " +
                                    "The second argument is the location of the data storage in the file system.");
            System.exit(1);
        }

        File dataDirectory = new File(args[0]);
        File storeDirectory = new File(args[1]);

        if (!dataDirectory.exists()) {
            Logger.getLogger("IndexEngine").log(Level.INFO, "The latimes.gz data file does not exist in the directory provided. Exiting...");
            System.exit(1);
        }

        if (storeDirectory.exists()) {
            Logger.getLogger("IndexEngine").log(Level.INFO, "The storage directory you provided already exists. Exiting...");
            System.exit(1);
        }

        Logger.getLogger("IndexEngine").log(Level.INFO, "DATA_PATH: " + args[0]);
        Logger.getLogger("IndexEngine").log(Level.INFO, "STORE_PATH: " + args[1]);
    }

    private static String processDocnoLine(String docnoLine) {
        // Extract trimmed DOCNO from line

        String docno = docnoLine.substring(DOCNO_START_TAG.length(), docnoLine.length() - (DOCNO_START_TAG.length() + 1));
        return docno.trim();
    }

    private static String[] getDateByDocno(String docno) {
        // Given DOCNO, extract embedded date

        String month = docno.substring(2, 4);
        String day = docno.substring(4, 6);
        String year = docno.substring(6, 8);
        return new String[]{month, day, year};
    }
}
