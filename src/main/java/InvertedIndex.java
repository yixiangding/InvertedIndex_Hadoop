import java.io.IOException;
import java.util.Comparator;
import java.util.StringTokenizer;
import java.util.TreeMap;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class InvertedIndex {

    /*
        Mapper:
        Generate term: docID list.
        Eg: "a: 51918182"
     */
    public static class TokenizerMapper
            extends Mapper<LongWritable, Text, Text, Text> {

        private Text word = new Text();
        private Text docID = new Text();

        public void map(LongWritable key, Text value, Context context
        ) throws IOException, InterruptedException {
            String line = value.toString();
            boolean isFirstLineOfDoc = updateCurrentDocID(line);
            if (isFirstLineOfDoc)
                trimLine(line, value);
            StringTokenizer itr = new StringTokenizer(value.toString());
            while (itr.hasMoreTokens()) {
                word.set(itr.nextToken());
                context.write(word, docID);
            }
        }

        private boolean updateCurrentDocID(String line) {
            char tab = '\t';
            int tabIndex = line.indexOf(tab);
            if (tabIndex == -1) return false;
            String newDocID = line.substring(0, tabIndex);
            docID.set(newDocID);
            return true;
        }

        private void trimLine(String line, Text value) {
            String newLine = line.substring(line.indexOf('\t') + 1);
            value.set(newLine);
        }
    }

    /*
        Reducer:
        Generate term: docID: frequency list (Inverted Index)
        Eg: "a: 51918182: 34"
     */
    public static class IndexReducer
            extends Reducer<Text, Text, Text, Text> {
        private Text result = new Text();

        public void reduce(Text key, Iterable<Text> docIDs,
                           Context context
        ) throws IOException, InterruptedException {
            TreeMap<String, Integer> termFreqMap = countTermFreq(docIDs);
            String resultString = getResultString(termFreqMap);
            result.set(resultString);
            context.write(key, result);
        }

        private TreeMap<String, Integer> countTermFreq(Iterable<Text> docIDs) {
            TreeMap<String, Integer> termFreqMap = new TreeMap<String, Integer>(new Comparator<String>() {
                public int compare(String o1, String o2) {
                    return o1.compareTo(o2);
                }
            });
            for (Text docID : docIDs) {
                String docIDString = docID.toString();
                if (termFreqMap.containsKey(docIDString)) {
                    termFreqMap.put(docIDString, termFreqMap.get(docIDString) + 1);
                } else {
                    termFreqMap.put(docIDString, 1);
                }
            }
            return termFreqMap;
        }

        private String getResultString(TreeMap<String, Integer> termFreqMap) {
            StringBuffer resultBuffer = new StringBuffer();
            for (String docId : termFreqMap.keySet()) {
                resultBuffer.append(docId + ":" + termFreqMap.get(docId) + " ");
            }
//            resultBuffer.append(termFreqMap.toString());
            return resultBuffer.toString();
        }
    }

    public static void main(String[] args) throws Exception {
        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf, "InvertedIndex");
        job.setJarByClass(InvertedIndex.class);
        job.setMapperClass(TokenizerMapper.class);
        job.setReducerClass(IndexReducer.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));
        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}
