// Based on Nimesh Ghelani's implementation based on code by Mark D. Smucker

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class QRels {
    HashMap<String, ArrayList<String>> queryToRelevantDocnos;
    HashMap<String, Judgement> judgements;

    public QRels() {
        queryToRelevantDocnos = new HashMap<>();
        judgements = new HashMap<>();
    }

    public void addJudgement(Judgement judgement) {
        // add judgement to dictionary for key

        if(queryToRelevantDocnos.containsKey(judgement.getKey())) {
            Logger.getLogger("Evaluation.QRels").log(Level.SEVERE, "Cannot have duplicate queryID and docID data points");
            System.exit(1);
        }

        judgements.put(judgement.getKey(), judgement);

        if(judgement.getRelevance() != 0) {
            if(!queryToRelevantDocnos.containsKey(judgement.getQueryId())) {
                queryToRelevantDocnos.put(judgement.getQueryId(), new ArrayList<>());
            }
            queryToRelevantDocnos.get(judgement.getQueryId()).add(judgement.getDocId());
        }
    }

    public Set<String> getQueryIds() {
        // get topics

        return this.queryToRelevantDocnos.keySet();
    }

    public int getRelevance(String queryId, String docId) {
        // check if document is relevant given topic and docno
        String key = queryId + "-" + docId;
        if(judgements.containsKey(key)) {
            return judgements.get(key).getRelevance();
        }
        return 0;
    }
}
