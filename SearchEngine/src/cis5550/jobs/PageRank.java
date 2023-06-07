package cis5550.jobs;

import cis5550.flame.FlameContext;
import cis5550.flame.FlamePair;
import cis5550.flame.FlamePairRDD;
import cis5550.flame.FlameRDD;
import cis5550.kvs.KVSClient;
import cis5550.kvs.Row;
import cis5550.tools.Hasher;
import cis5550.tools.Logger;
import cis5550.webserver.Server;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cis5550.jobs.Crawler.*;
import static java.lang.Math.*;

public class PageRank {
    private static final Logger logger = Logger.getLogger(Server.class);
    static Hasher hasher = new Hasher();
    private static final AtomicInteger count = new AtomicInteger(0);

    public static void main(String[] args) {
        logger.info("main seed url ");
    }

    public static void run(FlameContext flameContext, String[] args) throws Exception {
        KVSClient kvsClient = flameContext.getKVS();
        double threhold = Double.parseDouble(args[0]);


        FlameRDD stable = flameContext.fromTable("crawl", r -> {
            if (r.get("url") == null || r.get("page") == null) return null;
            ArrayList<String> arrays = extractURL(r);
            Set<String> results = new HashSet<String>();
            String i;
            String main_url = r.get("url");

            for (String entry : arrays) {
                if (entry == null) continue;
                if (entry.contains("redirect_uri=")) {

                } else if (entry.contains("..")) {
                    continue;
                } else if (entry.endsWith(".jpg") || entry.endsWith(".jpeg") || entry.endsWith(".gif") || entry.endsWith(".png") || entry.endsWith(".txt")) {
                    continue;
                } else if (entry.length() > 100) {
                    continue;
                } else if (entry.contains(",")) {
                    continue;
                } else {
                    String rowName = hasher.hash(entry);
                    results.add(hasher.hash(entry));
                }

            }
            main_url = hasher.hash(main_url);
            return main_url + "val;" + "1.0,1.0,urls:" + results;
//            }
        });

//

        FlamePairRDD map = stable.mapToPair(p -> {
            if (p == null) return null;
            return new FlamePair(p.split("val;")[0], p.split("val;")[1]);
        });
        stable.saveAsTable("stable");
        kvsClient.delete("stable");


        int i = 0;
        while (true) {
            i++;
            if (i > 2) {
                kvsClient.delete("joined_" + (i - 2));
                kvsClient.delete("diffs_" + (i - 2));
                kvsClient.delete("agg_" + (i - 2));
            }
            FlamePairRDD addRank = map.flatMapToPair(p -> {
                if (p == null) return null;
                Boolean selfLink = false;
                double current = Double.parseDouble(p._2().split(",")[0]);
                ArrayList<FlamePair> ranks = new ArrayList<>();

                if (p._2().split("urls:").length >= 2) {

                    String[] contains = p._2().split("urls:")[1].split(", ");

                    for (String url : contains) {
                        url = url.replaceAll("[\\[\\]]", "");

                        double newRank = 0.85 * current / contains.length;
                        if (!url.equals("")) {

                            ranks.add(new FlamePair(url, String.valueOf(newRank)));
                        }
                        if (url.equals(p._1())) {
                            selfLink = true;
                        }
                    }
                }
                if (!selfLink) ranks.add(new FlamePair(p._1().replaceAll("[\\[\\]]", ""), "0.0"));
                return ranks;
            });

            FlamePairRDD agg = addRank.foldByKey("0", (String a, String b) -> {

                if (!a.equals("")) {
                    return ("" + (Double.parseDouble(a) + Double.parseDouble(b))).replaceAll("[\\[\\]]", "");
                }
                return b;
            });

            addRank.saveAsTable("addRank_" + i);
            agg.saveAsTable("agg_" + i);
            kvsClient.delete("addRank_" + i);

            FlamePairRDD joined = map.join(agg).flatMapToPair(p -> {
                        String[] splits = p._2().split(",");
                        double current = Double.parseDouble(splits[0]);
                        ArrayList<FlamePair> ranks = new ArrayList<>();
                        int start = p._2().indexOf("[") + 1;
                        int end = p._2().indexOf("]");
                        String contains;
                        if (start > 0 && end > 0 && end > start) {
                            contains = p._2().substring(start, end);
                            double newRank = Double.parseDouble(splits[splits.length - 1]);
                            ranks.add(new FlamePair(p._1(), (newRank + 0.15) + "," + current + ",urls:" + "[" + contains + "]"));
                        } else {
                            ranks.add(new FlamePair(p._1(), p._2().substring(0, p._2().lastIndexOf(','))));
                        }
                        return ranks;
                    }
            );

            map = joined;

            joined.saveAsTable("joined_" + i);

            FlameRDD diffs = map.flatMap(p -> {
                String[] splits = p._2().split(",");
                double current = Double.parseDouble(splits[0]);
                double previous = Double.parseDouble(splits[1]);
                return Arrays.asList(String.valueOf(abs(current - previous)));
            });

            String max = diffs.fold("0", (s1, s2) -> String.valueOf(max(Double.parseDouble(s1), Double.parseDouble(s2))));
            if (args.length == 2) {
                double rate1 = Double.valueOf(args[0]);
                double ratio_input = Double.valueOf(args[1]) / 100.0;
                int count = diffs.count();
                if (count ==0 ) break;
                FlameRDD criterion = diffs.flatMap(m -> {
                    List<String> ret = new ArrayList<String>();
                    double value1 = Double.valueOf(m);
                    if (rate1 > value1) ret.add(m);
                    return ret;
                });

                Double ratio = Double.valueOf(criterion.count()) / Double.valueOf(count);
                if (ratio >= ratio_input) {
                    break;
                }

            }
            diffs.saveAsTable("diffs_" + i);

        }
        kvsClient.delete("pageranks");

        FlamePairRDD result = map.flatMapToPair(p -> {
            List<FlamePair> ret = new ArrayList<FlamePair>();
            String[] splits = p._2().split(",");
            String current = splits[0];
            flameContext.getKVS().put("pageranks", p._1(), "rank", current);
            return ret;
        });



    }

    static ArrayList<String> extractURL(Row r) {
        //TODO: still contains duplicated urls and "/"
        logger.debug("entering extractURL method");
        ArrayList<String> urls = new ArrayList<>();
        Set<String> uniqueUrls = new HashSet<>();
        if (r == null || !r.columns().contains("page") || !r.columns().contains("url")) {
            logger.debug("invalid Row found, failed to further extract urls");
            return null;
        }
        try {
            String page = r.get("page");
            String[] pageSplit = page.split("<a ");

            for (String p : pageSplit) {
                int endOfAnchor = p.indexOf(">");
                if (endOfAnchor > 0){
                    //int endOfAnchor = p.indexOf(">");
                    p = p.substring(0, endOfAnchor);
                    String[] tags = p.split(" ");

                    for (String tag : tags) {
                        if (tag.startsWith("href")){
                            int startIndex = tag.indexOf("\""); //TODO may fail due to broken html
                            int endIndex = tag.indexOf("\"", startIndex + 1);
                            if (startIndex < 0 || endIndex < 0 || startIndex + 1 > endIndex) {
                                logger.error("Malformed anchor href tag, failed to parse for \" \" : " + tag);
//                                kvs.put("failure", "unknown", "extractURL", r.get("url"));
                                continue;
                            }
                            String url = tag.substring(startIndex + 1, endIndex);
                            String normalizedBaseURL = r.get("url");
                            logger.debug("normalized base url = " + normalizedBaseURL);
                            String normalizedURL = getNormalizedURL(url, normalizedBaseURL);
                            logger.debug("extracted normalized url = " + normalizedURL);
                            if (normalizedURL!= null && normalizedURL.length() != 0 && !uniqueUrls.contains(normalizedURL)) {
                                urls.add(normalizedURL);
                                uniqueUrls.add(normalizedURL);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to extract urls from base url " + r.get("url"));
            e.printStackTrace();
        }
        return urls;
    }


}