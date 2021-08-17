import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TBG {
    static final double P_S_1_R_1 = 0.77;
    static final double  P_C_1_R_1 = 0.64;
    static final double P_C_1_R_0 = 0.39;
    static final double TS = 4.4;
    static final double gainAtK = P_S_1_R_1 * P_C_1_R_1;

    public static ArrayList<Double> calculateTBG(Results results, QRels qrels) {
        // calculates TBG

        ArrayList<Double> tbgs = new ArrayList<>();
        ArrayList<Integer> documentLengths = null;
        HashMap<String, Integer> docnoToInternalIdMap = new HashMap<>();

        // Initialize docnoToInternalID mapping and document lengths from HW2
        try {
            FileInputStream fis = new FileInputStream("/Users/billsheng/Desktop/541/store" + "/documentLengths.ser");
            ObjectInputStream ois = new ObjectInputStream(fis);
            documentLengths = (ArrayList<Integer>) ois.readObject();

            File mappingFile = new File("/Users/billsheng/Desktop/541/store" + "/IDMapping.txt");
            BufferedReader mappingReader = new BufferedReader(new FileReader(mappingFile));

            String mappingLine = mappingReader.readLine();

            while (mappingLine != null) {
                String[] mappings = mappingLine.split(" ");
                int internalId = Integer.parseInt(mappings[0]);
                String docno = mappings[1];

                docnoToInternalIdMap.put(docno, internalId);

                mappingLine = mappingReader.readLine();
            }
        } catch (IOException | ClassNotFoundException e) {
            Logger.getLogger("IREvaluator").log(Level.SEVERE, e.toString());
        }

        Object[] queries = qrels.getQueryIds().toArray();

        // Sort topic ID's in ascending order
        Arrays.sort(queries);

        for (Object topic : queries) {
            ArrayList<Result> topicResults = results.getResult(topic.toString());

            // No results means 0 score for topic
            if (topicResults == null) {
                tbgs.add(0.0);
                continue;
            }

            double tbg = 0;
            for (int resultIdx = 1; resultIdx <= topicResults.size(); resultIdx++) {
                if (qrels.getRelevance(topic.toString(), topicResults.get(resultIdx - 1).getDocno()) > 0) {
                    double expectedTimeToReachK = getExpectedTimeToReachK(resultIdx, qrels, topicResults, docnoToInternalIdMap, documentLengths, topic.toString());
                    double decay = getDecay(expectedTimeToReachK);
                    tbg += (decay * gainAtK);
                }
            }
            tbgs.add(tbg);
        }
        return tbgs;
    }

    public static double getDecay(double expectedTimeToReachK) {
        // calculates TBG decay

        return Math.exp((-1 * expectedTimeToReachK * (Math.log(2))) / 224);
    }

    private static double getExpectedTimeToReachK(int rankLimit, QRels qrels, ArrayList<Result> topicResults, HashMap<String, Integer> docnoToInternalIdMap, ArrayList<Integer> documentLengths, String topic) {
        // calculates TBG expected time to reach K

        double timeToReachRankK = 0;
        for (int resultIdx = 1; resultIdx < rankLimit; resultIdx++) {
            Result result = topicResults.get(resultIdx - 1);
            int id = docnoToInternalIdMap.get(result.getDocno());
            int documentLength = documentLengths.get(id);

            double td = (0.018 * documentLength) + 7.8;

            // probability value varies based on if document is relevant
            if (qrels.getRelevance(topic, result.getDocno()) > 0) {
                timeToReachRankK += (TS + (td * P_C_1_R_1));
            } else {
                timeToReachRankK += (TS + (td * P_C_1_R_0));
            }
        }
        return timeToReachRankK;
    }
}
