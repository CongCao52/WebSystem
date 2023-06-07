//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package cis5550.jobs;

import cis5550.flame.FlameContext;
import cis5550.flame.FlamePair;
import cis5550.flame.FlamePairRDD;
import cis5550.flame.FlameRDD;
import cis5550.kvs.KVSClient;
import cis5550.tools.Hasher;
import cis5550.tools.Logger;
import cis5550.tools.Stemmer;
import cis5550.webserver.Server;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class Indexer_new {
    private static final Logger logger = Logger.getLogger(Server.class);
    static Hasher hasher = new Hasher();

    public Indexer_new() {
    }

    public static void main(String[] args) {
        logger.info("main seed url ");
    }

    public static void run(FlameContext flameContext, String[] args) throws Exception {
        KVSClient kvsClient = flameContext.getKVS();
        FlameRDD urls = flameContext.fromTable("crawl", (r) -> {
            if (r.get("url") != null && r.get("page") != null) {
                if (r.get("url").length() <= 1000 && r.get("page").length() >= 100) {
                    if (r.get("url").contains(",")) {
                        return "";
                    } else if (r.get("url").contains("..")) {
                        return "";
                    } else {
                        Document doc = Jsoup.parse(r.get("page"));
                        String body = wordTrim(doc.body().text());
                        String title = wordTrim(doc.title());
                        String substring = body.substring(0, Math.min(100, body.length()));
                        String[] splits = body.replaceAll("[^a-zA-Z0-9]", " ").split("\\s");
                        String urlHash = Hasher.hash(r.get("url"));
                        return r.get("url") + ",page: " + title + " " + body;
                    }
                } else {
                    return "";
                }
            } else {
                return "";
            }
        });
        FlamePairRDD map = urls.mapToPair((p) -> {
            if (p.equals("")) {
                return null;
            } else {
                String[] splits = p.split(",page:");

                try {
                    return splits.length > 1 && splits[0] != null && splits[1] != null ? new FlamePair(splits[0], splits[1].replaceAll("<.*?>", " ").replaceAll("[^a-zA-Z0-9]", " ").toLowerCase()) : null;
                } catch (Exception var3) {
                    logger.info(p);
                    return null;
                }
            }
        });
        urls.saveAsTable("urls");
        kvsClient.delete("urls");
        FlamePairRDD flamePairRDD = map.flatMapToPair((p) -> {
            if (p == null) {
                return null;
            } else {
                String url = p._1();
                ArrayList<FlamePair> entries = new ArrayList();
                List<String> splits = List.of(p._2().split("\\s"));
                if (splits.size() < 10) {
                    return entries;
                } else {
                    Set<String> uniqueSet = new HashSet(splits);
                    String[] unique_wordsArray = (String[])uniqueSet.toArray(new String[uniqueSet.size()]);

                    for(int i = 0; i < unique_wordsArray.length; ++i) {
                        if (!unique_wordsArray[i].isEmpty()) {
                            String entry = unique_wordsArray[i];
                            String newWord = wordStem(entry);
                            if (entry.length() <= 10 && (!entry.matches("[0-9]+") || entry.length() == 4) && !entry.matches("[0-9]+[a-zA-z]+") && (!entry.matches(".*\\d+.*") || entry.matches("[0-9]+"))) {
                                try {
                                    if (!flameContext.getKVS().existsRow("dictionary", entry) && !flameContext.getKVS().existsRow("dictionary", newWord)) {
                                        continue;
                                    }
                                } catch (IOException var12) {
                                    continue;
                                }

                                String indices = "";
                                indices = indices + ": " + Collections.frequency(splits, entry);
                                FlamePair f = new FlamePair(entry, url + ":" + indices.substring(1));
                                entries.add(f);
                                if (!entry.equals(newWord)) {
                                    entries.add(new FlamePair(newWord, url + ":" + indices.substring(1)));
                                }
                            }
                        }
                    }

                    return entries;
                }
            }
        });
        map.saveAsTable("maps");
        kvsClient.delete("maps");
        FlamePairRDD flamePairRDD1 = flamePairRDD.foldByKey("", (a, b) -> {
            return !a.equals("") ? a + "," + b : b;
        });
        flamePairRDD.saveAsTable("assignWord");
        kvsClient.delete("assignWord");
        flamePairRDD1.flatMapToPair((p) -> {
            List<FlamePair> ret = new ArrayList();
            flameContext.getKVS().persist("index");
            flameContext.getKVS().put("index", p._1(), "urls", p._2());
            return ret;
        });
        flamePairRDD1.saveAsTable("agg");
        kvsClient.delete("agg");
    }

    public static String wordStem(String input) {
        Stemmer s = new Stemmer();
        char[] chars = input.toCharArray();
        s.add(chars, chars.length);
        s.stem();
        return s.toString();
    }

    public static String wordTrim(String input) {
        return input.replaceAll("<.*?>", "").replaceAll("&nbsp;", "\\s").replaceAll("&#160;", "\\s").replaceAll("&quot;", "\\s");
    }

    public static String getBody(String input) {
        String result = "";
        Pattern p = Pattern.compile("<(h|p).*?>(.*)<\\/(h|p).?>");

        String find;
        for(Matcher m = p.matcher(input); m.find(); result = result + " " + find) {
            find = m.group(2);
            find.replaceAll("&nbsp;", "\\s");
        }

        return result;
    }
}
