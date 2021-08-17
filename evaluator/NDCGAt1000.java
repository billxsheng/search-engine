import java.io.BufferedWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NDCGAt1000 {
    public static ArrayList<Double> calculateNDCGAt1000(Results results, QRels qrels) {
        // calculates NDCG at 1000

        ArrayList<Double> ndcgsAt1000 = new ArrayList<>();
        Object[] queries = qrels.getQueryIds().toArray();

        // Sort topic ID's in ascending order
        Arrays.sort(queries);
        for (Object topic : queries) {
            ArrayList<Result> topicResults = results.getResult(topic.toString());

            // No results means 0 score for topic
            if (topicResults == null) {
                ndcgsAt1000.add(0.0);
                continue;
            }

            // Prevent out of bounds exceptions
            // If results size < 1000, no relevant docs found at ranks > number of docs returned
            int rankLimit = Math.min(topicResults.size(), 1000);

            // Validate 1000 relevant docs exist for topic --> affects idcg
            int relDocumentsCount = Math.min(qrels.queryToRelevantDocnos.get(topic).size(), 1000);

            double dcg = 0;
            double idcg = 0;

            // calculate idcg
            for (int relDocIdx = 1; relDocIdx <= relDocumentsCount; relDocIdx++) {
                idcg += 1 / (Math.log(relDocIdx + 1) / Math.log(2));
            }

            // calculate dcg
            for (int resultIdx = 1; resultIdx <= rankLimit; resultIdx++) {
                Result result = topicResults.get(resultIdx - 1);
                if (qrels.getRelevance(topic.toString(), result.getDocno()) > 0) {
                    dcg += 1 / (Math.log(resultIdx + 1) / Math.log(2));
                }
            }

            double ndcg = dcg / idcg;
            ndcgsAt1000.add(ndcg);
        }
        return ndcgsAt1000;
    }
}
