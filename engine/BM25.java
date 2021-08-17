import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BM25 {
    // Initialize ranking algorithm constants
    static final double k1 = 1.2;
    static final double k2 = 7.0;
    static final double b = 0.75;
    static double AVG_DOC_LENGTH;
    static double TOTAL_NUMBER_DOCS;
    final static String Q0_VALUE = "Q0";
    final static String runTag = "bxshengAND";

    // Initialize data structures from index
    static BufferedWriter queryResultBW;
    static ArrayList<Integer> documentLengths;
    static Map<String, Integer> termToIdLexicon;
    static Map<Integer, String> idToTermLexicon;
    static Map<Integer, ArrayList<Integer>> invertedIndex;
    static HashMap<Integer, String> internalIdToDocnoMap = new HashMap<>();
    static HashMap<String, Integer> docnoToInternalIdMap = new HashMap<>();

    public static void main(String[] args) {
        // Initialize program arguments
        final String STORE_PATH = args[0];
        final String QUERIES_FILENAME = args[1];
        final String OUTPUT_FILENAME = args[2];

        // boolean that determines whether or not stemming is used
        final boolean STEM = Boolean.parseBoolean(args[3]);

        if (STEM) {
            System.out.println("Using stemmer and stem index");
        } else {
            System.out.println("Using regular index");
        }

        // Initialize data structures and writers
        initializeObjects(STORE_PATH);
        initializeMappingDict(STORE_PATH);
        initializeResultWriter(OUTPUT_FILENAME);

        // Calculate average document length
        double runningSum = 0.0;
        for (int length : documentLengths) {
            runningSum += length;
        }
        AVG_DOC_LENGTH = runningSum / documentLengths.size();
        TOTAL_NUMBER_DOCS = internalIdToDocnoMap.size();

        // Run BM25 Ranking Algorithm on topics
        long startTime = System.currentTimeMillis();
        try {
            String absolutePath = new File("").getAbsolutePath();
            File queriesDirectory = new File(absolutePath + "/data/" + QUERIES_FILENAME);
            BufferedReader queriesBR = new BufferedReader(new FileReader(queriesDirectory));

            String queryLine = queriesBR.readLine();

            while (queryLine != null) {
                String topicId = queryLine;
                queryLine = queriesBR.readLine();
                String query = queryLine;
                executeBM25Retrieval(query, topicId, STEM);
                queryLine = queriesBR.readLine();
            }
        } catch (IOException e) {
            Logger.getLogger("BM25").log(Level.SEVERE, e.toString());
        }
        long endTime = System.currentTimeMillis();
        System.out.println("Time taken: " + (endTime - startTime));
        closeWriter();
    }

    public static void executeBM25Retrieval(String query, String topic, boolean stem) throws IOException {
        // Given a query, run the BM25 ranking algorithm on the LATimes document collection
        ArrayList<String> tokens;

        // Tokenize the query based on how the document collection was tokenized
        if (stem) {
            tokens = DocumentUtils.tokenizeAndStem(query);
        } else {
            tokens = DocumentUtils.tokenize(query);
        }

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

        // Remove the top 1000 documents for the query from the priority queue
        // If 1000 rankings do not exist, remove until priority queue is empty
        while (rank <= 1000 && !priorityQueue.isEmpty()) {
            double[] pair = priorityQueue.remove();
            appendTRECResultToOutputFile((int) pair[0], topic, pair[1], rank);
            rank++;
        }

        Logger.getLogger("BM25").log(Level.INFO, "Executed query for topic " + topic + ": " + query);
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

    private static void appendTRECResultToOutputFile(int docId, String topicId, double score, int rank) throws IOException {
        // Append result to output file in TREC format

        String docno = internalIdToDocnoMap.get(docId);
        queryResultBW.write(topicId + " " + Q0_VALUE + " " + docno + " " + rank + " " + score + " " + runTag);
        queryResultBW.newLine();
    }

    private static void initializeObjects(String store_path) {
        // Initializes data saved in FS (lexicon dicts, inverted index, metadata, document lengths)

        try {
            Logger.getLogger("BM25").log(Level.INFO, "Deserializing lexicon dictionaries...");
            FileInputStream fis = new FileInputStream(store_path + "/termToIdLexicon.ser");
            ObjectInputStream ois = new ObjectInputStream(fis);
            termToIdLexicon = (HashMap) ois.readObject();

            fis = new FileInputStream(store_path + "/idToTermLexicon.ser");
            ois = new ObjectInputStream(fis);
            idToTermLexicon = (HashMap) ois.readObject();

            Logger.getLogger("BM25").log(Level.INFO, "Deserializing document lengths array...");
            fis = new FileInputStream(store_path + "/documentLengths.ser");
            ois = new ObjectInputStream(fis);
            documentLengths = (ArrayList<Integer>) ois.readObject();

            Logger.getLogger("BM25").log(Level.INFO, "Deserializing inverted index...");
            fis = new FileInputStream(store_path + "/invertedIndex.ser");
            ois = new ObjectInputStream(fis);
            invertedIndex = (HashMap) ois.readObject();

            ois.close();
            fis.close();
        } catch (IOException | ClassNotFoundException e) {
            Logger.getLogger("BM25").log(Level.SEVERE, e.toString());
        }
    }

    private static void initializeMappingDict(String storePath) {
        // Reads mapping file and creates dictionaries internal id -> DOCNO and DOCNO -> internal id

        Logger.getLogger("BM25").log(Level.INFO, "Initializing ID/DOCNO mapping dictionaries...");
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
        } catch (IOException e) {
            Logger.getLogger("BM25").log(Level.SEVERE, e.toString());
        }
    }

    private static void initializeResultWriter(String resultFilename) {
        // Initialize output writer for query results

        final String absolutePath = new File("").getAbsolutePath();
        File resultsFile = new File(absolutePath + "/../../" + resultFilename);

        try {
            FileOutputStream mappingFOS = new FileOutputStream(resultsFile);
            queryResultBW = new BufferedWriter(new OutputStreamWriter(mappingFOS));
        } catch (IOException e) {
            Logger.getLogger("BM25").log(Level.SEVERE, e.toString());
        }
    }

    private static void closeWriter() {
        // close output writer for query results

        try {
            queryResultBW.close();
        } catch (IOException e) {
            Logger.getLogger("BM25").log(Level.SEVERE, e.toString());
        }

    }
}
