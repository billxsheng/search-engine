import java.util.ArrayList;
import java.util.Arrays;

public class AveragePrecision {
    public static ArrayList<Double> calculateAveragePrecision(Results results, QRels qrels) {
        // calculates average precision

        ArrayList<Double> averagePrecisions = new ArrayList<>();
        Object[] queries = qrels.getQueryIds().toArray();

        // Sort topic ID's in ascending order
        Arrays.sort(queries);
        for (Object topic : queries) {
            ArrayList<Result> topicResults = results.getResult(topic.toString());

            // No results means 0 score for topic
            if (topicResults == null) {
                averagePrecisions.add(0.0);
                continue;
            }

            int numRelevantDocs = qrels.queryToRelevantDocnos.get(topic).size();

            double relevantResultsCounter = 0;
            double averagePrecisionSum = 0;
            int rank = 1;

            for (Result result : topicResults) {
                if (qrels.getRelevance(topic.toString(), result.getDocno()) > 0) {
                    relevantResultsCounter++;
                    averagePrecisionSum += relevantResultsCounter / rank;
                }
                rank++;
            }

            double averagePrecision = averagePrecisionSum / numRelevantDocs;
            averagePrecisions.add(averagePrecision);
        }
        return averagePrecisions;
    }
}
