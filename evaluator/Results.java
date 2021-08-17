// Based on Nimesh Ghelani's implementation based on code by Mark D. Smucker

import java.util.*;

public class Results {

    // Comparator to sort results
    public class SortResults implements Comparator<Result> {
        @Override
        public int compare(Result o1, Result o2) {
            if(o1.getScore() != o2.getScore()) {
                // Sort by decreasing score
                return -1 * Double.compare(o1.getScore(), o2.getScore());
            } else {
                // Sort by docno decreasing lexicographical order
                return -1 * o1.getDocno().compareTo(o2.getDocno());
            }
        }
    }

    public HashMap<String, ArrayList<Result>> queryToResults;

    public Results() {
        this.queryToResults = new HashMap<>();
    }

    public void addResult(String queryId, Result result) {
        // add result to dictionary for topic

        if(!queryToResults.containsKey(queryId)) {
            queryToResults.put(queryId, new ArrayList<>());
        }
        queryToResults.get(queryId).add(result);
    }

    public ArrayList<Result> getResult(String queryId) {
//        return results for given topic

        if(!queryToResults.containsKey(queryId)) {
            return null;
        }
        ArrayList<Result> results = queryToResults.get(queryId);

        // sort array before returning
        results.sort(new SortResults());
        return queryToResults.get(queryId);
    }
}
