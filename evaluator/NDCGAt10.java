import java.util.ArrayList;
import java.util.Arrays;

public class NDCGAt10 {
    public static ArrayList<Double> calculateNDCGAt10(Results results, QRels qrels) {
        // calculates NDCG at 10

        ArrayList<Double> ndcgsAt10 = new ArrayList<>();
        Object[] queries = qrels.getQueryIds().toArray();

        // Sort topic ID's in ascending order
        Arrays.sort(queries);
        for (Object topic : queries) {
            ArrayList<Result> topicResults = results.getResult(topic.toString());

            // No results means 0 score for topic
            if (topicResults == null) {
                ndcgsAt10.add(0.0);
                continue;
            }

            // Prevent out of bounds exceptions
            // If results size < 10, no relevant docs found at ranks > number of docs returned
            int rankLimit = Math.min(topicResults.size(), 10);

            // Validate 10 relevant docs exist for topic --> affects idcg
            int relDocumentsCount = Math.min(qrels.queryToRelevantDocnos.get(topic).size(), 10);

            double dcg = 0;
            double idcg = 0;

            // calculate idcg
            for (int relDocIdx = 1; relDocIdx <= relDocumentsCount ; relDocIdx++) {
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
            ndcgsAt10.add(ndcg);
        }
        return ndcgsAt10;
    }
}
