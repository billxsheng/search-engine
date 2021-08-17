// Based on Nimesh Ghelani's implementation based on code by Mark D. Smucker

public class Judgement {
    private String queryId;
    private String docId;
    private int relevance;

    public Judgement(String queryId, String docId, int relevance) {
        this.queryId = queryId;
        this.docId = docId;
        this.relevance = relevance;
    }

    public String getQueryId() {
        return queryId;
    }
    public String getDocId() {
        return docId;
    }

    public int getRelevance() {
        return relevance;
    }

    public String getKey() {
        // create key for judgement

        return this.queryId + "-" + this.docId;
    }
}
