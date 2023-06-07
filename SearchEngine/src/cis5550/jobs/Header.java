package cis5550.jobs;

import cis5550.flame.FlameContext;
import cis5550.flame.FlamePair;
import cis5550.flame.FlamePairRDD;
import cis5550.flame.FlameRDD;
import cis5550.kvs.KVSClient;
import cis5550.kvs.Row;
import cis5550.tools.Hasher;
import cis5550.tools.Logger;
import cis5550.tools.Stemmer;
import cis5550.tools.URLParser;
import cis5550.webserver.Server;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cis5550.jobs.Header.wordTrim;
import static java.lang.Math.log;
import static java.lang.Math.min;

public class Header {
    private static final Logger logger = Logger.getLogger(Server.class);
    static Hasher hasher = new Hasher();

    static URLParser urlParser = new URLParser();

    public static void main(String[] args) {
        logger.info("main seed url ");
    }

    public static void run(FlameContext flameContext, String[] args) throws Exception {

        KVSClient kvsClient = flameContext.getKVS();
        FlameRDD headers = flameContext.fromTable("crawl", r -> {
                    if (r.get("url") == null) return "";
                    if (r.get("page") == null) return "";
                    if (r.get("url").length() > 1000) return "";
                    if (r.get("url").contains(",")) return "";
                    if (r.get("url").contains("..")) return "";
                    if (r.get("page") != null && r.get("page").length() > 100) {
                        Document doc = Jsoup.parse(r.get("page"));
                        String body = wordTrim(doc.body().text());
                        String title = wordTrim(doc.title());
                        if (title.equals("")){
                            title = wordTrim(doc.select("h1").text());

                        }
                        String substring = body.substring(0, min(100, body.length()));
                        String[] splits = body.replaceAll("[^a-zA-Z0-9]", " ").split("\\s");
                        String urlHash = Hasher.hash(r.get("url"));
                        try {
                            flameContext.getKVS().persist("headers");
                            if (!flameContext.getKVS().existsRow("headers", urlHash))
                                flameContext.getKVS().put("headers", urlHash, "Context", r.get("url") + ",title: " + title + ", firstWords: " + substring + ", total_words: " + splits.length);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        return r.get("url") + ",page: " + title + " " + body;
                    }
                    return "";
                }
        );


        FlameRDD urls = flameContext.fromTable("headers", r -> {
            String context = r.get("Context");
            String[] splits = context.split(",title: " +
                    "");
            String url = splits[0];
            String title ="";
            if (splits.length >1) {
                title =splits[1].split(", firstWords:")[0];
            }
            String[] parsers = urlParser.parseURL(url);
            String host = "";
            String path ="";
            if (parsers.length >2) host = parsers[1];
            if (parsers.length >3) path = parsers[3];
            String res = ( url + ",context:" + host + " " + path +" "+ title).toLowerCase();

            return res;
        }
        );
//        FlameRDD urls = flameContext.fromTable("split", r -> {
//                return r.get("value");
//                }
//        );
        FlamePairRDD map = urls.mapToPair(p -> {
                    if (p == null) return null;
                    String[] splits = p.split(",context:");
                    String HashUrl = Hasher.hash(splits[0]);

                    try {
                        if (splits.length > 1) {
                            if (splits[0] != null && splits[1] != null)
                                return new FlamePair(HashUrl, p);
                        }
                    } catch (Exception e) {
                        logger.info(p);
                        return null;
                    }
                    return null;
                }
        );

        long current_time = System.currentTimeMillis();
        FlamePairRDD flamePairRDD = map.flatMapToPair(p -> {
            if (p == null) return null;
//            logger.info("join: " + p._2());

            ArrayList<FlamePair> entries = new ArrayList<>();

            String url = p._2().split(",context:")[0];
            if (p._2().split(",context:").length> 1) {
                String[] splits = p._2().replaceAll("<.*?>", " ").replaceAll("[^a-zA-Z0-9]", " ").toLowerCase().split("\\s");
                Set<String> uniqueSet = new HashSet<>(List.of(splits));
                String[] unique_wordsArray = uniqueSet.toArray(new String[uniqueSet.size()]);
                Set<String> stopWord = new HashSet<>();
                stopWord.add("org");
                stopWord.add("www");
                stopWord.add("com");
                stopWord.add("https");
                stopWord.add("html");
                stopWord.add("http");
                stopWord.add("edu");
//                stopWord.add("");
                int i = 1;
                for (String entry : unique_wordsArray) {

                    if (!entry.equals("")) {
                        String newWord = wordStem(entry);
                        if (entry.length() > 10) continue;
                        if (entry.matches("[0-9]+")) {
                            continue;
                        }
                        if (entry.matches("[0-9]+[a-zA-z]+")) continue;
                        if (entry.matches(".*\\d+.*") && !entry.matches("[0-9]+")) continue;
                        if (stopWord.contains(entry)) continue;

                        entries.add(new FlamePair(entry, url));
                    }

                }
            }

            return entries;
        });

        FlamePairRDD flamePairRDD1 = flamePairRDD.foldByKey("", (String a, String b) -> {
            if (!a.equals("")) {
                return a + "," + b;
            }
            return b;
        });
        FlamePairRDD result = flamePairRDD1.flatMapToPair(p -> {
            List<FlamePair> ret = new ArrayList<FlamePair>();
            flameContext.getKVS().persist("urls_index");
            flameContext.getKVS().put("urls_index", p._1(), "urls", p._2());

            return ret;
        });
    }

    public static String wordStem(String input) {
        Stemmer s = new Stemmer();
        char[] chars = input.toCharArray();
        s.add(chars, chars.length);
        s.stem();
        return s.toString();
    }

    public static String wordTrim(String input) {
        return input.replaceAll("<.*?>", "").replaceAll("&nbsp;", "\\s").replaceAll("&#160;", "\\s").replaceAll("&quot;","\\s");
    }



}
