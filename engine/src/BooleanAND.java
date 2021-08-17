import javax.swing.text.Document;
import java.io.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BooleanAND {
    static ArrayList<Integer> documentLengths;
    static Map<String, Integer> termToIdLexicon;
    static Map<Integer, String> idToTermLexicon;
    static Map<Integer, ArrayList<Integer>> invertedIndex;
    static HashMap<Integer, String> internalIdToDocnoMap = new HashMap<>();
    static HashMap<String, Integer> docnoToInternalIdMap = new HashMap<>();
    static HashMap<Integer, String[]> metadataDict = new HashMap<>();
    static BufferedWriter queryResultBW;
    final static String Q0_VALUE = "Q0";
    final static String runTag = "bxshengAND";


    public static void main(String[] args) {
        validateInputs(args);

        final String STORE_PATH = args[0];
        final String QUERIES_FILENAME = args[1];
        final String OUTPUT_FILENAME = args[2];

        initializeObjects(STORE_PATH);
        initializeMappingDict(STORE_PATH);
        initializeMetadataDict(STORE_PATH);
        initializeResultWriter(OUTPUT_FILENAME);

        // Read queries file, execute queries, and output results in TREC format
        try {
            String absolutePath = new File("").getAbsolutePath();
            File queriesDirectory = new File(absolutePath + "/data/" + QUERIES_FILENAME);
            BufferedReader queriesBR = new BufferedReader(new FileReader(queriesDirectory));

            String queryLine = queriesBR.readLine();

            while (queryLine != null) {
                String topicId = queryLine;
                queryLine = queriesBR.readLine();
                String query = queryLine;
                ArrayList<Integer> queryResults = executeQuery(query);
                if(!queryResults.isEmpty()) {
                    appendTRECResultToOutputFile(queryResults, topicId);
                } else {
                    Logger.getLogger("BooleanAND").log(Level.INFO, "No results for query: " + query);
                }
                queryLine = queriesBR.readLine();
            }
        } catch (IOException e) {
            Logger.getLogger("BooleanAND").log(Level.SEVERE, e.toString());
        }

        closeWriter();
    }

    private static ArrayList<Integer> executeQuery(String query) {
        // tokenize query, transform to Id's, fetch postings lists, and get result by intersecting

        Logger.getLogger("BooleanAND").log(Level.INFO, "Executing query: " + query);

        ArrayList<String> tokens = DocumentUtils.tokenize(query);
        ArrayList<Integer> tokenIds = new ArrayList<>();
        for(String token: tokens) {
            if(termToIdLexicon.containsKey(token)) {
                tokenIds.add(termToIdLexicon.get(token));
            }
        }

        ArrayList<ArrayList<Integer>> lists = new ArrayList<>();
        for(int id: tokenIds) {
            lists.add(invertedIndex.get(id));
        }

        // Sort posting lists by size
        lists.sort(Comparator.comparingInt(ArrayList::size));

        // Return if no postings lists fetched from query
        if(lists.isEmpty()) {
            return new ArrayList<>();
        }

        ArrayList<Integer> baseList = lists.get(0);

        // intersect all lists
        for(int listIdx = 1; listIdx < lists.size(); listIdx++) {
            baseList = intersectLists(baseList, lists.get(listIdx));
        }

        return baseList;
    }

    private static ArrayList<Integer> intersectLists(ArrayList<Integer> list1, ArrayList<Integer> list2) {
        // Given two lists (list1.size() <= list2.size()), intersect the documents that exist in both lists

        ArrayList<Integer> result = new ArrayList<>();
        int list1Idx = 0;
        int list2Idx = 0;

        while(list1Idx != list1.size() && list2Idx != list2.size()) {
            if(list1.get(list1Idx).equals(list2.get(list2Idx))) {
                result.add(list1.get(list1Idx));
                result.add(0);
                list1Idx += 2;
                list2Idx += 2;
            } else if(list1.get(list1Idx) < list2.get(list2Idx)) {
                list1Idx += 2;
            } else {
                list2Idx += 2;
            }
        }

        return result;
    }

    private static void appendTRECResultToOutputFile(ArrayList<Integer> queryResults, String topicId) throws IOException {
        // For each query result, append results to output file in TREC format

        int rank = 1;
        for(int resultIdx = 0; resultIdx < queryResults.size(); resultIdx += 2) {
            int id = queryResults.get(resultIdx);
            String docno = internalIdToDocnoMap.get(id);
            int score = (queryResults.size() / 2) - rank;
            queryResultBW.write(topicId + " " + Q0_VALUE + " " + docno + " " + rank + " " + score + " " + runTag);
            queryResultBW.newLine();
            rank++;
        }
    }

    private static void initializeObjects(String store_path) {
        // Initializes data saved in FS (lexicon dicts, inverted index, metadata, document lengths)

        try {
            Logger.getLogger("BooleanAND").log(Level.INFO, "Deserializing lexicon dictionaries...");
            FileInputStream fis = new FileInputStream(store_path + "/termToIdLexicon.ser");
            ObjectInputStream ois = new ObjectInputStream(fis);
            termToIdLexicon = (HashMap) ois.readObject();

            fis = new FileInputStream(store_path + "/idToTermLexicon.ser");
            ois = new ObjectInputStream(fis);
            idToTermLexicon = (HashMap) ois.readObject();

            Logger.getLogger("BooleanAND").log(Level.INFO, "Deserializing document lengths array...");
            fis = new FileInputStream(store_path + "/documentLengths.ser");
            ois = new ObjectInputStream(fis);
            documentLengths = (ArrayList<Integer>) ois.readObject();

            Logger.getLogger("BooleanAND").log(Level.INFO, "Deserializing inverted index...");
            fis = new FileInputStream(store_path + "/invertedIndex.ser");
            ois = new ObjectInputStream(fis);
            invertedIndex = (HashMap) ois.readObject();

            ois.close();
            fis.close();
        } catch (IOException | ClassNotFoundException e) {
            Logger.getLogger("BooleanAND").log(Level.SEVERE, e.toString());
        }
    }

    private static void initializeMappingDict(String storePath) {
        // Reads mapping file and creates dictionaries internal id -> DOCNO and DOCNO -> internal id

        Logger.getLogger("BooleanAND").log(Level.INFO, "Initializing ID/DOCNO mapping dictionaries...");
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
            Logger.getLogger("BooleanAND").log(Level.SEVERE, e.toString());
        }
    }

    private static void initializeMetadataDict(String storePath) {
        // Reads metadata file and creates dictionary internal id -> metadata

        Logger.getLogger("BooleanAND").log(Level.INFO, "Initializing metadata dictionaries...");
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
        } catch (IOException e) {
            Logger.getLogger("BooleanAND").log(Level.SEVERE, e.toString());
        }
    }

    private static void validateInputs(String[] args) {
        // Validate arguments provided and exit program if errors encountered

        if (args.length < 3) {
            Logger.getLogger("BooleanAND")
                    .log(Level.INFO,
                            "Exiting... HELP: The BooleanAND program takes three arguments. " +
                                    "The first argument is the location of the data storage in the file system. " +
                                    "The second argument is the name of the queries file which should be located within the " +
                                    "root of this project directory. " +
                                    "The third argument is the name of the file to store the output of the program.");
            System.exit(1);
        }

        File storeDirectory = new File(args[0]);
        String absolutePath = new File("").getAbsolutePath();
        File queryDirectory = new File(absolutePath + "/data/" + args[1]);

        if (!storeDirectory.exists()) {
            Logger.getLogger("BooleanAND").log(Level.INFO, "The storage directory you provided does not exist. Exiting...");
            System.exit(1);
        }

        if (!queryDirectory.exists()) {
            Logger.getLogger("BooleanAND").log(Level.INFO, "Could not find the queries file. Exiting...");
            System.exit(1);
        }
    }

    private static void initializeResultWriter(String resultFilename) {
        // Initialize output writer for query results

        final String absolutePath = new File("").getAbsolutePath();
        File resultsFile = new File(absolutePath + "/outputs/" + resultFilename);

        try {
            FileOutputStream mappingFOS = new FileOutputStream(resultsFile);
            queryResultBW = new BufferedWriter(new OutputStreamWriter(mappingFOS));
        } catch (IOException e) {
            Logger.getLogger("BooleanAND").log(Level.SEVERE, e.toString());
        }
    }

    private static void closeWriter() {
        // close output writer for query results

        try {
            queryResultBW.close();
        } catch (IOException e) {
            Logger.getLogger("BooleanAND").log(Level.SEVERE, e.toString());
        }

    }
}
