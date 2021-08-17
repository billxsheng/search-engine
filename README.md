# search-engine
A text-based document search engine that retrieves the top 10 most relevant documents from the 1989/1990 LATimes collection. The system features engines for indexing documents, saving metadata, creating snippets (query-biased summaries), and generating rankings. A lexicon is used to keep track of the terms in the document collection. The indexing engine creates an inverted index that maps terms to documents that contain the respective term. The system leverages the BM25 ranking algorithm to generate rankings. 

# Setup
To run the QueryEngine program, navigate into `search-engine/engine/src`
- Build the class files with the following commands:
  - `javac QueryEngine.java`
  - Errors may arise if you are not using Java 16 (latest version)
- Run the QueryEngine program:
  - `java QueryEngine {store_path}`


To run the IREvaluator, navigate into `search-engine/evaluator`
- Build the class files with the following commands:
  - `javac IREvaluator.java`
  - Errors may arise if you are not using Java 16 (latest version)
- Run the IREvaluator program:
  - `java IREvaluator {path_to_results_file} {path_to_qrels_file}`
