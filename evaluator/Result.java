// Based on Nimesh Ghelani's implementation based on code by Mark D. Smucker

public class Result {
    private String docno;
    private double score;
    private int rank;

    public String getDocno() {
        return docno;
    }

    public double getScore() {
        return score;
    }

    public int getRank() {
        return rank;
    }

    public Result(String docno, double score, int rank) {
        this.docno = docno;
        this.score = score;
        this.rank = rank;
    }
}
