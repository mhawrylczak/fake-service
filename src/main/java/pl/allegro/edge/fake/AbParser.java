package pl.allegro.edge.fake;


import java.io.*;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AbParser {
    private final static Pattern documentPath = Pattern.compile("^Document Path\\:\\s*([\\w/]+)\\s*$");
    private final static Pattern users = Pattern.compile("^Concurrency Level\\:\\s*([\\w]+)\\s*$");
    private final static Pattern completeRequests = Pattern.compile("^Complete requests\\:\\s*([\\w]+)\\s*$");
    private final static Pattern failedRequests = Pattern.compile("^Failed requests\\:\\s*([\\w]+)\\s*$");
    private final static Pattern rps = Pattern.compile("^Requests per second\\:\\s*([\\d,\\.]+).*$");
    private final static Pattern mean = Pattern.compile("^Time per request\\:\\s*([\\d,\\.]+).*$");
    private final static Pattern fifty = Pattern.compile("\\s*50\\%\\s*([\\d]+).*$");
    private final static Pattern ninety = Pattern.compile("\\s*90\\%\\s*([\\d]+).*$");
    private final static Pattern ninetyNine = Pattern.compile("\\s*99\\%\\s*([\\d]+).*$");

    private final static Pattern non2xxResponses = Pattern.compile("^Non-2xx responses\\:\\s*([\\d]+)\\s*$");
    private final static Pattern totalTransferred = Pattern.compile("^Total transferred\\:\\s*([\\d]+).*$");

    private final static Pattern errors = Pattern.compile("^\\s*\\(Connect\\:\\s*(\\d+),\\s*Receive\\:\\s*(\\d+),\\s*Length\\:\\s*(\\d+),\\s*Exceptions\\:\\s*(\\d+).*$");

    public static void main(final String[] args) throws IOException {
        StringJoiner header = new StringJoiner(";");
        for (String col : Arrays.asList("path", "users", "requests", "failed", "err_conn", "err_rcv", "err_len", "err_ex", "non2xx","rps", "mean", "50", "90", "99")) {
            header.add(col);
        }
        System.out.println(header);
        try (BufferedReader reader = new BufferedReader(new FileReader((new File(args[0]))))) {
            Iterable<String> testResults;
            while (null != (testResults = readTestResults(reader))) {
                StringJoiner line = new StringJoiner(";");
                for (String cell : testResults) {
                    line.add(cell.replace('.', ','));
                }
                System.out.println(line);
            }
        }


    }

    private static Iterable<String> readTestResults(BufferedReader reader) throws IOException {
        String path = findValue(reader, documentPath);
        if (path == null) {
            return null;
        }
        List<String> result = new ArrayList<>();

        result.add(path);
        result.add(findValue(reader, users));
        result.add(findValue(reader, completeRequests));
        String failedRequestsValue = findValue(reader, failedRequests);
        result.add(failedRequestsValue);
        if (Integer.parseInt(failedRequestsValue) > 0 ){
            result.addAll(getErrors(reader.readLine()));
        }else{
            result.addAll(Arrays.asList("0", "0", "0", "0"));
        }

        result.add(findOptional(reader, non2xxResponses, totalTransferred, "0"));
        result.add(findValue(reader, rps));
        result.add(findValue(reader, mean));
        result.add(findValue(reader, fifty));
        result.add(findValue(reader, ninety));
        result.add(findValue(reader, ninetyNine));
        return result;
    }

    private static Collection<String> getErrors(String s) {
        Matcher matcher = errors.matcher(s);
        if (matcher.matches()){
            return Arrays.asList(matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4));
        }
        return Arrays.asList("0", "0", "0", "0");
    }

    private static String findOptional(BufferedReader reader, Pattern pattern, Pattern end, String defaultValue) throws IOException {
        String line;
        while (null != (line = reader.readLine())) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                return matcher.group(1);
            }

            Matcher endMatcher = end.matcher(line);
            if (endMatcher.matches()){
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static String findValue(BufferedReader reader, Pattern pattern) throws IOException {
        String line;
        while (null != (line = reader.readLine())) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        return null;
    }

}
