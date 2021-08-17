import java.io.*;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IREvaluator {
    // Format results files
    static DecimalFormat result_format = new DecimalFormat();
    static BufferedWriter outputWriter;

    public static void main(String[] args) {
        // Remove scientific notation
        result_format.setMaximumFractionDigits(100);

        // Initialize path variables
        final String RESULTS_PATH = args[0];
        final String QREL_PATH = args[1];
        File resultsFile = new File(RESULTS_PATH);
        File qrelFile = new File(QREL_PATH);

        // Initialize data structures
        QRels qrels = new QRels();
        Results results = new Results();

        // Import QRel judgements
        // Based on Nimesh Ghelani's implementation based on code by Mark D. Smucker
        try {
            BufferedReader qrelBR = new BufferedReader(new FileReader(qrelFile));
            String qrelLine = qrelBR.readLine();

            while (qrelLine != null) {
                String[] qRelData = qrelLine.split("\\s");
                Judgement judgement = new Judgement(qRelData[0], qRelData[2], Integer.parseInt(qRelData[3]));
                qrels.addJudgement(judgement);
                qrelLine = qrelBR.readLine();
            }
            System.out.println("QRels Initialized");
        } catch (IOException e) {
            Logger.getLogger("IREvaluator").log(Level.SEVERE, e.toString());
        }

        // Import Results file
        // Based on Nimesh Ghelani's implementation based on code by Mark D. Smucker
        try {
            BufferedReader resultsBR = new BufferedReader(new FileReader(resultsFile));
            String resultsLine = resultsBR.readLine();
            Set<String> visited = new HashSet<>();
            String globalRunId = null;

            while (resultsLine != null) {

                // Validate results line is valid
                String[] resultsData = resultsLine.split("\\s");
                if (resultsData.length != 6) {
                    Logger.getLogger("IREvaluator").log(Level.SEVERE, "Results file should have exactly 6 columns");
                    System.exit(1);
                }

                String queryId = resultsData[0];
                String docno = resultsData[2];
                String runId = resultsData[5];

                // Validate DOCNO format
                if (!docno.matches("LA[0-9]{6}-[0-9]{4}")) {
                    Logger.getLogger("IREvaluator").log(Level.SEVERE, "DOCNO not formatted correctly.");
                    System.exit(1);
                }

                // Make sure runId is consistent throughout results file
                if (globalRunId == null) {
                    globalRunId = runId;
                } else if (!globalRunId.equals(runId)) {
                    Logger.getLogger("IREvaluator").log(Level.SEVERE, "Mismatching runIDs in results file");
                    System.exit(1);
                }


                // Validate score and rank are of correct type
                double score = -1;
                int rank = -1;
                try {
                    score = Double.parseDouble(resultsData[4]);
                    rank = Integer.parseInt(resultsData[3]);
                } catch (Exception e) {
                    Logger.getLogger("IREvaluator").log(Level.SEVERE, "Results file contains unexpected types");
                    System.exit(1);
                }

                // Check duplicates
                String key = queryId + "-" + docno;
                if (visited.contains(key)) {
                    Logger.getLogger("IREvaluator").log(Level.SEVERE, "Duplicate query_id, doc_id in results file");
                    System.exit(1);
                }
                visited.add(key);
                results.addResult(queryId, new Result(docno, score, rank));

                resultsLine = resultsBR.readLine();
            }
            System.out.println("Results Initialized");
        } catch (IOException e) {
            Logger.getLogger("IREvaluator").log(Level.SEVERE, e.toString());
        }

        // Run measures
        initializeResultWriter(resultsFile.getName().substring(0, resultsFile.getName().lastIndexOf(".")));
        ArrayList<Double> averagePrecisions = AveragePrecision.calculateAveragePrecision(results, qrels);
        ArrayList<Double> precisionsAt10 = PrecisionAt10.calculatePrecisionAt10(results, qrels);
        ArrayList<Double> ndcgsAt10 = NDCGAt10.calculateNDCGAt10(results, qrels);
        ArrayList<Double> ndcgsAt1000 = NDCGAt1000.calculateNDCGAt1000(results, qrels);
        ArrayList<Double> tbgs = TBG.calculateTBG(results, qrels);

        // Configure output format
        DecimalFormat df = new DecimalFormat("#.###");
        df.setRoundingMode(RoundingMode.HALF_UP);

        // Calculate averages
        double runningSumAP = 0;
        for(Double ap: averagePrecisions) {
            runningSumAP += ap;
        }

        double runningSumP10 = 0;
        for(Double p_10: precisionsAt10) {
            runningSumP10 += p_10;
        }

        double runningSumNDCG10 = 0;
        for(Double ncdg_cut_10: ndcgsAt10) {
            runningSumNDCG10 += ncdg_cut_10;
        }

        double runningSumNDCG1000 = 0;
        for(Double ndcg_cut_1000: ndcgsAt1000) {
            runningSumNDCG1000 += ndcg_cut_1000;
        }

        double runningSumTBG = 0;
        for(Double tbg: tbgs) {
            runningSumTBG += tbg;
        }

        Object[] queries = qrels.getQueryIds().toArray();
        Arrays.sort(queries);

        // print per topic results to files for each effectiveness measure
        for(int printIterator = 0; printIterator < queries.length; printIterator++) {
            try {
                outputWriter.write("ap" + "\t\t" + queries[printIterator] + "\t\t" + result_format.format(averagePrecisions.get(printIterator)));
                outputWriter.newLine();
            } catch (IOException e) {
                Logger.getLogger("IREvaluator").log(Level.SEVERE, e.toString());
            }
        }

        for(int printIterator = 0; printIterator < queries.length; printIterator++) {
            try {
                outputWriter.write("p_10" + "\t\t" + queries[printIterator] + "\t\t" + result_format.format(precisionsAt10.get(printIterator)));
                outputWriter.newLine();
            } catch (IOException e) {
                Logger.getLogger("IREvaluator").log(Level.SEVERE, e.toString());
            }
        }

        for(int printIterator = 0; printIterator < queries.length; printIterator++) {
            try {
                outputWriter.write("ndcg_cut_10" + "\t\t" + queries[printIterator] + "\t\t" + result_format.format(ndcgsAt10.get(printIterator)));
                outputWriter.newLine();
            } catch (IOException e) {
                Logger.getLogger("IREvaluator").log(Level.SEVERE, e.toString());
            }
        }

        for(int printIterator = 0; printIterator < queries.length; printIterator++) {
            try {
                outputWriter.write("ndcg_cut_1000" + "\t\t" + queries[printIterator] + "\t\t" + result_format.format(ndcgsAt1000.get(printIterator)));
                outputWriter.newLine();
            } catch (IOException e) {
                Logger.getLogger("IREvaluator").log(Level.SEVERE, e.toString());
            }
        }

        for(int printIterator = 0; printIterator < queries.length; printIterator++) {
            try {
                outputWriter.write("tbg" + "\t\t" + queries[printIterator] + "\t\t" + result_format.format(tbgs.get(printIterator)));
                outputWriter.newLine();
            } catch (IOException e) {
                Logger.getLogger("IREvaluator").log(Level.SEVERE, e.toString());
            }
        }

        // Print aggregate results
        System.out.println("Aggregated results for " + resultsFile.getName() + ":");
        System.out.println("Mean Average Precision: " + df.format(runningSumAP/averagePrecisions.size()));
        System.out.println("Mean P@10: " + df.format(runningSumP10/precisionsAt10.size()));
        System.out.println("Mean NDCG@10: " + df.format(runningSumNDCG10/ndcgsAt10.size()));
        System.out.println("Mean NDCG@1000: " + df.format(runningSumNDCG1000/ndcgsAt1000.size()));
        System.out.println("Mean TBG: " + df.format(runningSumTBG/tbgs.size()));

        closeWriter();
    }

    private static void initializeResultWriter(String fileName) {
        // Initialize output writer for evaluation results

        System.out.println("Creating IR measures file: " + fileName + "-measures.txt");

        final String absolutePath = new File("").getAbsolutePath();

        File resultsFile = new File(absolutePath + "/outputs/" + fileName + "-measures.txt");

        try {
            FileOutputStream mappingFOS = new FileOutputStream(resultsFile);
            outputWriter = new BufferedWriter(new OutputStreamWriter(mappingFOS));
        } catch (IOException e) {
            Logger.getLogger("IREvaluator").log(Level.SEVERE, e.toString());
        }
    }

    private static void closeWriter() {
        // close output writer for evaluation results

        try {
            outputWriter.close();
        } catch (IOException e) {
            Logger.getLogger("BooleanAND").log(Level.SEVERE, e.toString());
        }

    }
}
