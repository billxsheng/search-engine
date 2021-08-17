import java.util.ArrayList;
import java.util.Arrays;

public class PrecisionAt10 {
    public static ArrayList<Double> calculatePrecisionAt10(Results results, QRels qrels) {
        // calculates precision at 10

        ArrayList<Double> precisionsAt10 = new ArrayList<>();
        Object[] queries = qrels.getQueryIds().toArray();

        // Sort topic ID's in ascending order
        Arrays.sort(queries);
        for (Object topic : queries) {
            ArrayList<Result> topicResults = results.getResult(topic.toString());

            // No results means 0 score for topic
            if (topicResults == null) {
                precisionsAt10.add(0.0);
                continue;
            }

            int relevantResultsCounter = 0;

            // Prevent out of bounds exceptions
            // If results size < 10, no relevant docs found at ranks > number of docs returned
            int rankLimit = Math.min(topicResults.size(), 10);

            for (int resultIdx = 1; resultIdx <= rankLimit; resultIdx++) {
                if (qrels.getRelevance(topic.toString(), topicResults.get(resultIdx - 1).getDocno()) > 0) {
                    relevantResultsCounter++;
                }
            }

            double precisionAt10 = relevantResultsCounter / 10.0;
            precisionsAt10.add(precisionAt10);
        }
        return precisionsAt10;
    }
}
