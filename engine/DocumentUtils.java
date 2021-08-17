import java.util.ArrayList;

public class DocumentUtils {
    private static final String PARAGRAPH_START_TAG = "<P>";
    private static final String PARAGRAPH_END_TAG = "</P>";

    public static String extractTextByTag(ArrayList<String> rawDocument, String tag, String closingTag) {
        // Get text within a given tag for a raw document, ignoring nested paragraph tags

        StringBuffer documentText = new StringBuffer();

        for (int idx = 0; idx < rawDocument.size(); idx++) {
            if (rawDocument.get(idx).equals(tag)) {
                for (int endIdx = idx + 1; endIdx < rawDocument.size(); endIdx++) {
                    if (rawDocument.get(endIdx).equals(closingTag)) {
                        break;
                    } else if (!rawDocument.get(endIdx).equals(PARAGRAPH_START_TAG) && !rawDocument.get(endIdx).equals(PARAGRAPH_END_TAG)) {
                        documentText.append(rawDocument.get(endIdx));
                    }
                }
            }
        }

        return documentText.toString();
    }

    public static ArrayList<String> tokenize(String documentText) {
        // tokenize raw text
        // Based on SimpleTokenizer by Trevor Strohman

        documentText = documentText.toLowerCase();
        ArrayList<String> tokens = new ArrayList<>();
        int start = 0;
        int idx;
        for (idx = 0; idx < documentText.length(); ++idx) {
            if (!Character.isLetterOrDigit(documentText.charAt(idx))) {
                if (start != idx) {
                    String token = documentText.substring(start, idx);
                    tokens.add(token);
                }
                start = idx + 1;
            }
        }

        if (start != idx) {
            tokens.add(documentText.substring(start, idx));
        }

        return tokens;
    }

    public static ArrayList<String> tokenizeAndStem(String documentText) {
        // tokenize raw text
        // Based on SimpleTokenizer by Trevor Strohman

        documentText = documentText.toLowerCase();
        ArrayList<String> tokens = new ArrayList<>();
        int start = 0;
        int idx;
        for (idx = 0; idx < documentText.length(); ++idx) {
            if (!Character.isLetterOrDigit(documentText.charAt(idx))) {
                if (start != idx) {
                    String token = documentText.substring(start, idx);
                    tokens.add(PorterStemmer.stem(token));
                }
                start = idx + 1;
            }
        }

        if (start != idx) {
            tokens.add(PorterStemmer.stem(documentText.substring(start, idx)));
        }

        return tokens;
    }
}
