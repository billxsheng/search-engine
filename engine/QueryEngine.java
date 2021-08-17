import java.io.*;
import java.time.Month;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class QueryEngine {
    // Initialize ranking algorithm constants and string constants
    static final double k1 = 1.2;
    static final double k2 = 7.0;
    static final double b = 0.75;
    static double AVG_DOC_LENGTH;
    static double TOTAL_NUMBER_DOCS;
    private static final String GRAPHIC_START_TAG = "<GRAPHIC>";
    private static final String GRAPHIC_END_TAG = "</GRAPHIC>";
    private static final String TEXT_START_TAG = "<TEXT>";
    private static final String TEXT_END_TAG = "</TEXT>";
    private static String storePath = "";


    // Initialize data structures from index
    static ArrayList<Integer> documentLengths;
    static Map<String, Integer> termToIdLexicon;
    static Map<Integer, String> idToTermLexicon;
    static Map<Integer, ArrayList<Integer>> invertedIndex;
    static HashMap<Integer, String> internalIdToDocnoMap = new HashMap<>();
    static HashMap<String, Integer> docnoToInternalIdMap = new HashMap<>();
    private static HashMap<Integer, String[]> metadataDict = new HashMap<>();

    public static void main(String[] args) {
        storePath = args[0];

        initializeObjects(storePath);
        initializeMappingDict(storePath);
        initializeMetadataDict(storePath);

        // Calculate average document length and total number of documents for BM25 algorithm
        double runningSum = 0.0;
        for (int length : documentLengths) {
            runningSum += length;
        }
        AVG_DOC_LENGTH = runningSum / documentLengths.size();
        TOTAL_NUMBER_DOCS = internalIdToDocnoMap.size();

        // User input flow
        while (true) {
            // Initialize scanner
            Scanner userInput = new Scanner(System.in);
            System.out.println("\n");
            System.out.println("Please enter your query:");
            String query = userInput.nextLine();
            System.out.println("Executing query: " + query);
            System.out.println("\n");

            // Execute query and calculate total time in seconds
            long startTime = System.currentTimeMillis();
            ArrayList<Integer> results = executeBM25Retrieval(query);
            long endTime = System.currentTimeMillis();
            double totalSeconds = (endTime - startTime) / 1000.0;
            System.out.println("Query retrieval took " + totalSeconds + " seconds.");

            // Prompt user to input next action
            while (true) {
                System.out.println("\n");
                System.out.println("Enter rank number to view document.");
                System.out.println("Enter N to create new query.");
                System.out.println("Enter Q to quit program.");
                String userAction = userInput.nextLine();

                // Quit on input Q
                if (userAction.equals("Q")) {
                    System.out.println("Exiting Program...");
                    System.exit(0);
                }

                // New query on input N (break to outer loop)
                if (userAction.equals("N")) {
                    break;
                }

                // Parse and validate rank number
                try {
                    int i = Integer.parseInt(userAction);
                    if (i < 11 && i > 0) {
                        int id = results.get(i - 1);
                        System.out.println("\n");
                        System.out.println("Viewing document of rank: " + i);
                        System.out.println("_____________________");
                        displayRawDocument(id);
                        System.out.println("_____________________");
                    } else {
                        System.out.println("Rank entered out of range. Must be between 1 and 10.");
                    }
                } catch (Exception e) {
                    System.out.println("Invalid Input.");
                }
            }
        }
    }

    public static ArrayList<Integer> executeBM25Retrieval(String query) {
        // Given a query, run the BM25 ranking algorithm on the LATimes document collection

        // Tokenize query
        ArrayList<String> tokens = DocumentUtils.tokenize(query);

        // Initialize accumulator
        HashMap<Integer, Double> accumulator = new HashMap<>();

        // Initialize priority queue, sort by max score
        PriorityQueue<double[]> priorityQueue = new PriorityQueue<>((a, b) -> {
            double scoreA = a[1];
            double scoreB = b[1];
            return -1 * Double.compare(scoreA, scoreB);
        });

        ArrayList<Integer> tokenIds = new ArrayList<>();
        HashMap<Integer, Integer> queryTermFrequency = new HashMap<>();

        // Convert tokens to token ID's and populate the query term frequency map
        for (String token : tokens) {
            if (termToIdLexicon.containsKey(token)) {
                tokenIds.add(termToIdLexicon.get(token));

                int id = termToIdLexicon.get(token);
                if (!queryTermFrequency.containsKey(id)) {
                    queryTermFrequency.put(id, 1);
                } else {
                    queryTermFrequency.put(id, queryTermFrequency.get(id) + 1);
                }
            }
        }

        // Term-at-a-time algorithm
        for (int id : tokenIds) {

            // Fetch postings list from inverted index
            ArrayList<Integer> postings = invertedIndex.get(id);
            int iterator = 0;

            // Loop through the postings list
            while (iterator < postings.size()) {
                int docId = postings.get(iterator);
                int count = postings.get(iterator + 1);
                int numDocumentsWithTerm = postings.size() / 2;

                double K = calculateK(docId);
                double tfDoc = calculateTFDoc(K, count);
                double tfQuery = calculateTFQuery(queryTermFrequency.get(id));
                double idf = calculateIDF(numDocumentsWithTerm);

                // Compute partial score
                double score = idf * tfQuery * tfDoc;

                // Update accumulator score for doc
                if (!accumulator.containsKey(docId)) {
                    accumulator.put(docId, score);
                } else {
                    accumulator.put(docId, accumulator.get(docId) + score);
                }
                iterator += 2;
            }
        }

        // Add document scores to priority queue
        for (int key : accumulator.keySet()) {
            double[] docScorePair = new double[2];
            docScorePair[0] = key;
            docScorePair[1] = accumulator.get(key);
            priorityQueue.add(docScorePair);
        }

        int rank = 1;

        ArrayList<Integer> result = new ArrayList<>();

        // Inform user no results found
        if(priorityQueue.isEmpty()) {
            System.out.println("No results found for query: " + query);
            System.out.println("_____________________");
            System.out.println("\n");
            return null;
        }

        // Take top 10 documents or until priority queue is empty
        while (rank <= 10 && !priorityQueue.isEmpty()) {
            double[] pair = priorityQueue.remove();
            int docId = (int) pair[0];
            result.add(docId);
            outputResult(rank, docId, tokens);
            rank++;
        }

        return result;
    }

    private static double calculateK(int docId) {
        // Calculates the K value

        return k1 * ((1 - b) + (b * (documentLengths.get(docId) / AVG_DOC_LENGTH)));
    }

    private static double calculateTFDoc(double K, int tfInDoc) {
        // Calculates the term frequency for the document

        return ((k1 + 1) * tfInDoc) / (tfInDoc + K);
    }

    private static double calculateTFQuery(int tfInQuery) {
        // Calculates the term frequency for the query

        return ((k2 + 1) * tfInQuery) / (k2 + tfInQuery);
    }

    private static double calculateIDF(int numDocumentsWithTerm) {
        // Calculates the inverse document frequency for the term

        return Math.log((TOTAL_NUMBER_DOCS - numDocumentsWithTerm + 0.5) / (numDocumentsWithTerm + 0.5));
    }

    private static void outputResult(int rank, int docId, ArrayList<String> queryTokens) {
        // Output ranking results

        String docno = metadataDict.get(docId)[0];
        String date = metadataDict.get(docId)[1];
        String headline = metadataDict.get(docId)[2];
        String summary = generateQueryBiasedSummary(date, docno, queryTokens);

        // Check if headline exists
        if (headline.equals("")) {
            System.out.println(rank + ". " + summary.substring(0, 50) + "... (" + getFormattedDate(date) + ")");
            System.out.println(summary + " (" + docno + ")");
        } else {
            System.out.println(rank + ". " + headline + "(" + getFormattedDate(date) + ")");
            System.out.println(summary + " (" + docno + ")");
        }

        System.out.println("_____________________");
        System.out.println("\n");
    }

    private static String getFormattedDate(String date) {
        // Format date with forward slashes

        return date.substring(0, 2) + "/" + date.substring(2, 4) + "/" + date.substring(4, 6);
    }

    private static String generateQueryBiasedSummary(String date, String docno, ArrayList<String> queryTokens) {
        // Generate query biased summary given a query and a document

        // Fetch raw document
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

        StringBuilder documentText = new StringBuilder();

        // Parse raw document to extract text from TEXT and GRAPHIC tags
        for (int idx = 0; idx < rawDocument.size(); idx++) {
            if (rawDocument.get(idx).equals(TEXT_START_TAG)) {
                for (int endIdx = idx + 1; endIdx < rawDocument.size(); endIdx++) {
                    if (rawDocument.get(endIdx).equals(TEXT_END_TAG)) {
                        idx = endIdx;
                        break;
                    } else {
                        documentText.append(rawDocument.get(endIdx));
                    }
                }
            } else if (rawDocument.get(idx).equals(GRAPHIC_START_TAG)) {
                for (int endIdx = idx + 1; endIdx < rawDocument.size(); endIdx++) {
                    if (rawDocument.get(endIdx).equals(GRAPHIC_END_TAG)) {
                        idx = endIdx;
                        break;
                    } else {
                        documentText.append(rawDocument.get(endIdx));
                    }
                }
            }
        }

        // Strip tags from text
        String rawText = documentText.toString().replaceAll("<[^>]*>", "");

        // Split text into sentences
        String[] sentences = rawText.split("(?<=[!?.])");

        // Initialize priority queue
        PriorityQueue<int[]> priorityQueue = new PriorityQueue<>((a, b) -> {
            int scoreA = a[1];
            int scoreB = b[1];
            return -1 * Integer.compare(scoreA, scoreB);
        });

        // Calculate score for each sentence and add to priority queue
        for (int sentenceIdx = 0; sentenceIdx < sentences.length; sentenceIdx++) {
            ArrayList<String> tokenizedSentence = DocumentUtils.tokenize(sentences[sentenceIdx]);

            // Ignore sentences with less than 5 words
            if (tokenizedSentence.size() < 5) {
                continue;
            }

            int l = 0;
            int c = 0;
            int d = 0;
            int runningK = 0;
            int k = 0;

            if (sentenceIdx == 0) {
                l = 2;
            } else if (sentenceIdx == 1) {
                l = 1;
            }

            for (String queryToken : queryTokens) {
                if (tokenizedSentence.contains(queryToken)) {
                    d++;
                }
            }

            boolean isContiguous = false;

            for (String sentenceToken : tokenizedSentence) {
                if (queryTokens.contains(sentenceToken)) {
                    c++;
                    if (!isContiguous) {
                        isContiguous = true;
                        runningK = 1;
                    } else {
                        runningK++;
                    }
                } else {
                    isContiguous = false;
                    k = Math.max(runningK, k);
                }
            }

            int v = l + c + d + k;

            int[] pair = new int[2];
            pair[0] = sentenceIdx;
            pair[1] = v;
            priorityQueue.add(pair);
        }

        // Take top two sentences from priority queue
        StringBuilder summary = new StringBuilder();
        for (int counter = 0; counter < 2; counter++) {
            if (priorityQueue.size() == 0) {
                break;
            } else {
                int[] pair = priorityQueue.remove();
                int sentenceIdx = pair[0];
                summary.append(sentences[sentenceIdx]);
                summary.append(" ");
            }
        }

        String[] summaryWords = summary.toString().split(" ");
        if(summaryWords.length > 50) {
            StringBuilder trimmedSummary = new StringBuilder();
            String prefix = "";
            for(int wordCounter = 0; wordCounter < 35; wordCounter++) {
                trimmedSummary.append(prefix);
                prefix = " ";
                trimmedSummary.append(summaryWords[wordCounter]);
            }
            trimmedSummary.append("...");
            return trimmedSummary.toString();
        }
        return summary.toString();
    }

    private static void displayRawDocument(int docId) {
        // Given a document internal ID, output the raw document
        String docno = internalIdToDocnoMap.get(docId);
        String date = metadataDict.get(docId)[1];

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

        for (String line : rawDocument) {
            System.out.println(line);
        }
    }

    private static void initializeObjects(String store_path) {
        // Initializes data saved in FS (lexicon dicts, inverted index, metadata, document lengths)

        try {
            FileInputStream fis = new FileInputStream(store_path + "/termToIdLexicon.ser");
            ObjectInputStream ois = new ObjectInputStream(fis);
            termToIdLexicon = (HashMap) ois.readObject();

            fis = new FileInputStream(store_path + "/idToTermLexicon.ser");
            ois = new ObjectInputStream(fis);
            idToTermLexicon = (HashMap) ois.readObject();

            System.out.println("Lexicons Loaded");

            fis = new FileInputStream(store_path + "/documentLengths.ser");
            ois = new ObjectInputStream(fis);
            documentLengths = (ArrayList<Integer>) ois.readObject();

            System.out.println("Document Lengths Loaded");

            fis = new FileInputStream(store_path + "/invertedIndex.ser");
            ois = new ObjectInputStream(fis);
            invertedIndex = (HashMap) ois.readObject();

            System.out.println("Inverted Index Loaded");

            ois.close();
            fis.close();
        } catch (IOException | ClassNotFoundException e) {
            Logger.getLogger("BM25").log(Level.SEVERE, e.toString());
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

            System.out.println("Mapping Dictionaries Loaded");
        } catch (IOException e) {
            Logger.getLogger("BM25").log(Level.SEVERE, e.toString());
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
            System.out.println("Metadata Dictionary Loaded");
        } catch (IOException e) {
            Logger.getLogger("DocumentFetcher").log(Level.SEVERE, e.toString());
        }
    }
}
