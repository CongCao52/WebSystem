package cis5550.jobs;

import cis5550.flame.FlameContext;
import cis5550.flame.FlameRDD;
import cis5550.kvs.KVSClient;
import cis5550.kvs.Row;
import cis5550.tools.Hasher;
import cis5550.tools.Logger;
import cis5550.tools.URLParser;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import org.jsoup.Jsoup;

public class Crawler {
    private static final Logger logger = Logger.getLogger(Crawler.class);
    private static final String userAgent = "cis5550-crawler";
    private static final String allowCrawlerAccess = "Allow";
    private static final String disallowCrawlerAccess = "Disallow";
    private static final String defaultCrawlerAccess = "Not Found";
    private static final String crawlDelayRule = "Crawl-delay";
    private static final String defaultCrawlerDelayInString = "1";
    private static final String hostTable = "hosts";
    private static final String crawlTable = "crawl";
    private static final String pageTable = "hashPage";
    private static List<String> blackList;

    public static void run(FlameContext ctx, String[] args) {
        logger.debug("in crawler run func: ctx = " + ctx);
        String seedURL, blackListTable, multiSeedPath;
        if (args.length < 2) {
            ctx.output("Error: seed URL not found or blackList table name not provided");
        } else {
            ctx.output("OK");
        }

        blackListTable = args[1];
        seedURL = args[0];
        logger.debug("seedURL = " + seedURL + "blackListTable = " + blackListTable);

        try {
            //Creat persistent tables
            ctx.getKVS().persist(blackListTable);
            ctx.getKVS().persist(crawlTable);
            ctx.getKVS().persist(hostTable);
            ctx.getKVS().persist(pageTable);
            ctx.getKVS().persist("crawl_url_only");
            ctx.getKVS().persist("failure");


            LinkedList<String> list = new LinkedList<String>();
            list.add(getNormalizedBaseURL(seedURL));

            //check if there are multiple seeds provided:
            if (args.length >= 3) {
                try {
                    multiSeedPath = args[2];
                    File multiSeeds = new File(multiSeedPath);
                    if (!multiSeeds.exists()) {
                        System.out.println("Multi-seed-crawling not enabled as seeds.txt file not found");
                        logger.debug("Multi-seed-crawling not enabled as seeds.txt file not found");
                    }
                    List<String> allSeeds = Files.readAllLines(Paths.get(multiSeedPath));
                    System.out.println("Multiseeds include: ");
                    for (String extraSeed : allSeeds) {
                        System.out.println(extraSeed);
                        list.add(getNormalizedBaseURL(extraSeed));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    logger.error("Error reading seeds file or no such file");
                }
            }

            FlameRDD urlQueue = ctx.parallelize(list);
            int iteration = 0;
            while (urlQueue.count() != 0) {
                logger.debug("Entering whileloop with urlQueue.count() = " + urlQueue.count() + " and iteration #" + iteration);
//                if (i == 1) break;
//            logger.debug("urlQueue.toString() = " + urlQueue.toString());
                int finalIteration = iteration;
                urlQueue = urlQueue.flatMap(s -> processSeedURL(s, ctx, blackListTable, finalIteration));
//                Thread.sleep(1000); removed as this is for hw
                logger.debug("Done iteration #" + iteration + " with urlQueue.count() = " + urlQueue.count());
                iteration++;
            }
            logger.debug("###Complete analyzing the queue!");
        } catch (Exception e) {
            logger.error("Exception in run function");
            e.printStackTrace();
        }

    }

    private static void processBlackList(FlameContext ctx, String blackListTable) throws IOException {
        logger.debug("in processBlacklist func");
        blackList = new ArrayList<>();
        logger.debug("ctx in processBlacklist is: " + ctx);
        Iterator<Row> rows = ctx.getKVS().scan(blackListTable);
        logger.debug("finished scanning tables in processBlacklist func");
        while (rows.hasNext()) {
            blackList.add(rows.next().key());
        }
        logger.debug("finished creating Blacklist");
    }

    private static Iterable<String> processSeedURL(String seedURL, FlameContext ctx, String blackListTable, int iteration) throws IOException {
        Long startProcess = System.currentTimeMillis();
        logger.debug("lambda func with seedURL = " + seedURL);

        try {
            if (isUnwantedURL(ctx, seedURL, blackListTable)) {
                logger.debug("SeedURL filtered out. On black list or is abnormally long: " + seedURL);
                return new ArrayList<>();
            }
            logger.debug("done checking unwantedURL");
            String URLHash = Hasher.hash("" + seedURL);
            if (ctx.getKVS().existsRow(crawlTable, URLHash)) {
                logger.debug("row " + URLHash + " exists in crawl, so return empty list to flatMap job");
                return new ArrayList<>();
            }
            logger.debug("done checking existsRow");

            // seed url is supposed to normalized at this point. parse it.
            String[] parsedSeedURL = URLParser.parseURL(seedURL);
            String seedHost = parsedSeedURL[1];
            String seedProtocol = parsedSeedURL[0];
            String seedPort = parsedSeedURL[2];
            String robotsURL = seedProtocol + "://" + seedHost + ":" + seedPort + "/robots.txt";
            logger.debug("done parsedSeedURL");

            Long donePreCheck = System.currentTimeMillis();
            Long timeOfPreCheck  = donePreCheck - startProcess;
            logger.debug("Time of preCheck in processURL = " + timeOfPreCheck);

            // Step 1. Request for /robots.txt
//            logger.info("ctx.getKVS().existsRow(\"hosts\", " + seedHost + ") = " + ctx.getKVS().existsRow("hosts", seedHost));

            if (!ctx.getKVS().existsRow(hostTable, seedHost) || !ctx.getKVS().getRow(hostTable, seedHost).columns().contains("robotsRequested")) {
                HttpURLConnection conRobots = (HttpURLConnection) (new URL(robotsURL)).openConnection();
                conRobots.setRequestMethod("GET");
                conRobots.setRequestProperty("User-Agent", userAgent);
                conRobots.setConnectTimeout(1000);
                conRobots.setReadTimeout(1000);
                conRobots.connect();
                logger.debug("Requested robots.txt for  " + seedHost + " via: " + robotsURL);
                ctx.getKVS().put(hostTable, seedHost, "robotsRequested", "done");
                if (conRobots.getResponseCode() == 200) {

                    // opens input stream from the HTTP connection and save robots.txt to hosts table
                    InputStream inputStream = conRobots.getInputStream();
                    byte[] buffer = inputStream.readAllBytes();
                    ctx.getKVS().put(hostTable, seedHost, "robotsContent", buffer);
                    logger.debug("added robots.txt: " + buffer);
                    inputStream.close();
                    System.out.println("File downloaded");
                }
                conRobots.disconnect();
            }

            //TODO sitemap
            double crawlDelay = 1000;

            //Step 1.1 Checking rules
            if (ctx.getKVS().existsRow(hostTable, seedHost) && ctx.getKVS().getRow(hostTable, seedHost).get("robotsContent") != null) {
//                logger.info("robots.txt we found: " + ctx.getKVS().getRow(hostTable, seedHost).get("robotsContent"));
                String[] lines = ctx.getKVS().getRow(hostTable, seedHost).get("robotsContent").split("\r?\n|\r");

                String[] rules = checkRuleFromRobots(userAgent, lines, seedURL);
                // rules[0] = disallow/allow/not found; rules[1] = crawl-delay in second as string
                if (rules[0].equals(disallowCrawlerAccess)) {
                    logger.debug(seedURL + " was disallowed by useragent: " + userAgent);
                    return new ArrayList<>();

                } else if (rules[0].equals(defaultCrawlerAccess)) {
                    rules = checkRuleFromRobots("*", lines, seedURL); //TODO duplicated code
                    if (rules[0].equals(disallowCrawlerAccess)) {
                        logger.debug(seedURL + " was disallowed by useragent: " + "*");
                        return new ArrayList<>();
                    }
                }
                // update crawlDelay value
                try {
                    crawlDelay = Double.parseDouble(rules[1]) * 1000;
                    logger.debug("Found crawl-delay = " + crawlDelay);
                } catch (NumberFormatException nef) {
                    logger.error("Failed parsing crawl-delay");
                    nef.printStackTrace();
                }
                logger.debug(seedURL + " was allowed by robots.txt");
            }


            //Step 1.2 Rate limiting
            if (ctx.getKVS().existsRow(hostTable, seedHost) && ctx.getKVS().getRow(hostTable, seedHost).get("lastAccessTime") != null) {
                if (Long.parseLong(ctx.getKVS().getRow(hostTable, seedHost).get("lastAccessTime")) + crawlDelay > System.currentTimeMillis()) {
                    logger.warn("Too soon to reach " + seedHost + " as current time = " + System.currentTimeMillis() + " and last accessed at " + ctx.getKVS().getRow(hostTable, seedHost).get("lastAccessTime") + " crawl-delay = " + crawlDelay);
                    return List.of(seedURL);
                }
            }

            Long doneStep1 = System.currentTimeMillis();
            Long timeOfStep1  = doneStep1 - donePreCheck;
            logger.debug("Time of step1 in processURL = " + timeOfStep1);

            // Step 2. Head request
            logger.debug("Sending head request for " + seedURL);
            HttpURLConnection conHead = (HttpURLConnection) (new URL(seedURL)).openConnection();
            conHead.setRequestMethod("HEAD");
            conHead.setRequestProperty("User-Agent", userAgent);
            conHead.setInstanceFollowRedirects(false);
            conHead.setConnectTimeout(1000);
            conHead.setReadTimeout(1000);
            conHead.connect();

            Long timeOfHeadConnect  = System.currentTimeMillis() - doneStep1;
            logger.debug("Time of headConnect in processURL = " + timeOfHeadConnect);

            ctx.getKVS().put(hostTable, seedHost, "lastAccessTime", String.valueOf(System.currentTimeMillis()));
//            ctx.getKVS().put("hosts_domain_only", seedHost, "", "");
            logger.debug("updated lastAccessTime of " + seedHost + " to be" + String.valueOf(System.currentTimeMillis()));

            Row r = new Row(URLHash);
            r.put("url", seedURL);
            r.put("responseCode", String.valueOf(conHead.getResponseCode()));
            logger.debug("Response code from head request = " + conHead.getResponseCode() + " with URLHash = " + URLHash);

            if (conHead.getContentType() != null) r.put("contentType", conHead.getContentType());

            if (conHead.getContentLengthLong() != -1) r.put("length", String.valueOf(conHead.getContentLengthLong()));

            ctx.getKVS().putRow(crawlTable, r);
            ctx.getKVS().put("crawl_url_only", seedURL, "url_in_crawlTable", URLHash);

            if (!isRedirect(conHead.getResponseCode()) && conHead.getResponseCode() != 200) {
                logger.error("Error code from Head request, not redirects nor 200, with code = " + conHead.getResponseCode());
                conHead.disconnect();
                return new ArrayList<>();
            }

            ArrayList<String> redirectURLs = new ArrayList<>();
            if (isRedirect(conHead.getResponseCode())) {
                logger.debug("Head request, found redirects with code = " + conHead.getResponseCode());
                logger.debug("header of Location = " + conHead.getHeaderFields().get("Location"));
                List<String> redirects = conHead.getHeaderFields().get("Location");
                if (redirects != null) {
                    for (String url : redirects) {
                        redirectURLs.add(getNormalizedURL(url, seedURL));
                        logger.debug("added " + getNormalizedURL(url, seedURL) + " to redirectURLs");
                    }
                }
                conHead.disconnect();
                return redirectURLs;
            }
            conHead.disconnect();

            Long doneStep2 = System.currentTimeMillis();
            Long timeOfStep2  = doneStep2 - doneStep1;
            logger.debug("Time of step2 in processURL = " + timeOfStep2);

            // Step 3. GET request (HEAD must be 200 if reach this point)
            String contentType = conHead.getContentType();
            if ((contentType.equals("text/html") || contentType.equals("text/html; charset=UTF-8") || contentType.equals("text/html; charset=utf-8"))) {
                logger.debug("Sending GET request for " + seedURL);
                HttpURLConnection con = (HttpURLConnection) (new URL(seedURL)).openConnection();
                con.setRequestMethod("GET");
                con.setRequestProperty("User-Agent", userAgent);
                con.setInstanceFollowRedirects(false);
                con.setConnectTimeout(1000);
                con.setReadTimeout(1000);
                con.connect();

                Long timeOfGetConnect  = System.currentTimeMillis() - doneStep2;
                logger.debug("Time of GetConnect in processURL = " + timeOfGetConnect);

                ArrayList<String> extractedURLs = new ArrayList<>();
                logger.debug("Response code from GET request = " + con.getResponseCode() + " with row key = " + URLHash);
                ctx.getKVS().put(crawlTable, URLHash, "responseCode", String.valueOf(con.getResponseCode()));
//            r.put("responseCode", String.valueOf(con.getResponseCode()));
                Long startReadingGetResponse = System.currentTimeMillis();
                if (con.getResponseCode() == 200) {
                    InputStream is = con.getInputStream();
//                    ctx.getKVS().put(crawlTable, URLHash, "page", is.readAllBytes());
                    String page = new String(is.readAllBytes(), StandardCharsets.UTF_8);

                    //Filter out other languages:
                    String lang = Jsoup.parse(page).select("html").first().attr("lang");
                    if (lang.length() > 0 && !lang.toLowerCase().contains("en")) {
                        logger.debug("Filtering out languages other than English: " + seedURL);
                        return new ArrayList<>();
                    }

                    //Content-seen: filter out pages that have the exact same content as
                    //another page that has already been crawled
                    String hashPage = Hasher.hash(page);
                    if (ctx.getKVS().existsRow(pageTable, hashPage)) {
                        String originalURL = new String(ctx.getKVS().get(pageTable, hashPage, "url"), StandardCharsets.UTF_8);
                        ctx.getKVS().put(crawlTable, URLHash, "canonicalURL", originalURL);
                        logger.debug("found duplicated page content of " + seedURL + " & " + originalURL);
                    } else {
                        ctx.getKVS().put(pageTable, hashPage, "url", seedURL);
                        Long beforePutPageToCrawl = System.currentTimeMillis();
                        ctx.getKVS().put(crawlTable, URLHash, "page", page);
                        Long afterPutPageToCrawl  = System.currentTimeMillis() - beforePutPageToCrawl;
                        logger.debug("Time of PutPageToCrawl in processURL = " + afterPutPageToCrawl);
                    }
                Long timeOfReadingGetResponse  = System.currentTimeMillis() - startReadingGetResponse;
                logger.debug("Time of reading and processing GET response in processURL = " + timeOfReadingGetResponse);

                Long startExtract = System.currentTimeMillis();
                extractedURLs = extractURL(ctx.getKVS().getRow(crawlTable, URLHash), iteration);
                Long doneExtract = System.currentTimeMillis();
                Long timeOfExtract  = doneExtract - startExtract;
                logger.debug("Time of extract in processURL = " + timeOfExtract);
                logger.debug("got 200 response-> added page to row: " + URLHash + " url = " + ctx.getKVS().get(crawlTable, URLHash, "url") + " in crawl table");
            }

                Long doneStep3 = System.currentTimeMillis();
                Long timeOfStep3  = doneStep3 - doneStep2;
                logger.debug("Time of step3 in processURL = " + timeOfStep3);
                con.disconnect();
                return extractedURLs;
            }

        } catch (Exception e) {
            logger.error("Failed in processSeedURL function: " + seedURL);
            //ctx.getKVS().putRow("failure", new Row(seedURL));
            e.printStackTrace();
            return new ArrayList<>();
        }
        return new ArrayList<>();
    }

    /**
     * Filter out urls that are lengthy and are on blacklist
     * @param ctx
     * @param seedURL
     * @param blackListTable
     * @return
     * @throws IOException
     */
    private static boolean isUnwantedURL(FlameContext ctx, String seedURL, String blackListTable) throws IOException {
        logger.debug("in isunwatedURL func");
        if (blackList == null) {
            logger.debug("blacklist == null");
            processBlackList(ctx, blackListTable);
        }
        logger.debug("done checking null blacklist");
        for (String b : blackList) {
            if (seedURL.startsWith(b)) return true;
        }
        logger.debug("done checking all urls in blacklist");
        return seedURL.length() > 150;
    }

    static String[] checkRuleFromRobots(String userAgent, String[] lines, String seedURL) {
        String[] parsedSeedURL = URLParser.parseURL(seedURL);
        String shortedURL = parsedSeedURL[3];
        logger.debug("In checkingRuleFromRobots for " + userAgent + " and found shortedURL = " + shortedURL);
        //TODO refactor logics in while loop
        String[] rules = {defaultCrawlerAccess, defaultCrawlerDelayInString};
        int i = 0;
        while (i < lines.length) {
            if (lines[i].equals("User-agent: " + userAgent)) {
                i++;
                while (i < lines.length && !lines[i].startsWith("User-agent:") && rules[0].equals(defaultCrawlerAccess)) {
                    String[] rule = lines[i].split(": ");
                    if (rule.length != 2 ) {
                        i++;
                        continue;
                    }
//                    logger.info("rule line = " + lines[i] + " ; parse it to get String[]: " + rule[0] + " " + rule[1]);
                    rules = checkSingleRuleFromRobotsForAnUserAgent(rule[0], rule[1], shortedURL);
                    i++;
                }
                break;
            }
            i++;
        }
        return rules;
    }

    private static String[] checkSingleRuleFromRobotsForAnUserAgent(String rule, String prefix, String shortedURL) {
        String[] rules = {defaultCrawlerAccess, defaultCrawlerDelayInString};
        if (shortedURL.startsWith(prefix)) {
            if (rule.equals(allowCrawlerAccess)) {
                logger.debug("shortedURL start with " + prefix + " so allow");
                rules[0] = allowCrawlerAccess;
            }
            else if (rule.equals(disallowCrawlerAccess)) {
                logger.debug("shortedURL start with " + prefix + " so disallow");
                rules[0] = disallowCrawlerAccess;
            }
        }
        else if (rule.equals(crawlDelayRule)) {
            logger.debug("Crawl-delay rule found: " + rule + " " + prefix);
            rules[1] = prefix;
        }
        return rules;
    }

    private static boolean isRedirect(int responseCode) {
        return responseCode == 301 || responseCode == 302 || responseCode == 303 || responseCode == 307 || responseCode == 308;
    }

    static ArrayList<String> extractURL(Row r, int iteration) {
//        logger.info("entering extractURL method");
        ArrayList<String> urls = new ArrayList<>();
        Set<String> uniqueUrls = new HashSet<>();
        if (r == null || !r.columns().contains("page") || !r.columns().contains("url")) {
            logger.debug("invalid Row found, failed to further extract urls");
            return new ArrayList<>();
        }
        try {
            String page = r.get("page").toString();
            String[] pageSplit = page.split("<a ");

            int i = 0;
            int urlCount = 0;
            int extractLimit = iteration < 4 ? 50 : 10;

            while (urlCount <= extractLimit && i < pageSplit.length) {
                int endOfAnchor = pageSplit[i].indexOf(">");
                if (endOfAnchor > 0){
                    pageSplit[i] = pageSplit[i].substring(0, endOfAnchor);
                    String[] tags = pageSplit[i].split(" ");

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
                                // Temp modification: Avoid duplication from last crawl
                                if (urlCount <=3) {
                                    urlCount++;
                                    continue;
                                }
                                urls.add(normalizedURL);
                                uniqueUrls.add(normalizedURL);
                                urlCount++;
                            }
                        }
                    }
                }
                i++;
            }
            logger.debug("Finished extracting 10 pages (or it has < 10 pages) from " + r.get("url"));
        } catch (Exception e) {
            logger.error("Failed to extract urls from base url " + r.get("url"));
            e.printStackTrace();
        }
        return urls;
    }

    /**
     * Filter out URLs that 1) have a protocol other than http or https,
     * and/or 2) end in .jpg, .jpeg, .gif, .png, or .txt.
     * @param url
     * @return
     */
    private static boolean passedURLFiler(String url) {
        String[] parsedURL = URLParser.parseURL(url);
        String protocol = parsedURL[0];
        String host = parsedURL[1];
        String port = parsedURL[2];
//        logger.info("parsed url in filter func = " + protocol + " " + host + " " + port + " " +  parsedURL[3]);
        if (!(protocol.equals("http") || protocol.equals("https"))) return false;
        return !url.endsWith(".jpg") && !url.endsWith(".jpeg") && !url.endsWith(".gif") && !url.endsWith(".png") && !url.endsWith(".txt");
    }

    static String getNormalizedBaseURL(String baseURL) {
        String[] parsedSeedURL = URLParser.parseURL(baseURL);
        String baseProtocol = parsedSeedURL[0];
        String baseHost = parsedSeedURL[1];
        String basePort = parsedSeedURL[2];
        logger.debug("parsed base url = " + baseProtocol + " " + baseHost + " " + basePort + " " +  parsedSeedURL[3]);
        String normalizedURL = baseURL;
        if (baseProtocol == null || !(baseProtocol.equals("http") || baseProtocol.equals("https"))) {
            logger.warn("no http/https found in base url");
            return "";
        } else if (baseHost == null) {
            logger.warn("no host found in base url");
            return "";
        } else if (basePort == null) {
            String defaultPort = "http".equals(baseProtocol) ? "80" : "443";
            normalizedURL = baseProtocol + "://" + baseHost + ":" + defaultPort + parsedSeedURL[3];
        }
        logger.debug("normalizedBaseURL = " + normalizedURL);
        return normalizedURL;
    }

    static String getNormalizedURL(String url, String base) {

        if (base == null || base.length() == 0) {
            logger.warn("Found broken base url. Failed to normalize URL");
            return "";
        }
        if (url == null || url.length() == 0) {
            logger.warn("Found invalid url during extraction. Failed to normalize URL");
            return "";
        }
//        logger.info("Entering normalization func for url: " + url);
        String trimmedUrl = url, normalizedURL = url;
        int numberSign = url.indexOf("#");
        if (numberSign >= 0 ) {
            trimmedUrl = url.substring(0, numberSign);
            if (trimmedUrl.length() == 0) {
                return base;
            }
        }
        String[] parsedBaseURL = URLParser.parseURL(base);
        String baseProtocol = parsedBaseURL[0];
        String baseHost = parsedBaseURL[1];
        String basePort = parsedBaseURL[2];
//        logger.info("parsed base url = " + baseProtocol + " " + baseHost + " " + basePort + " " +  parsedBaseURL[3]);

        String[] parsedURL = URLParser.parseURL(trimmedUrl);
        String protocol = parsedURL[0];
        String host = parsedURL[1];
        String port = parsedURL[2];
//        logger.info("parsed url = " + protocol + " " + host + " " + port + " " +  parsedURL[3]);

        String baseURLRoot = baseProtocol + "://" + baseHost + ":" + basePort + "/"; // e.g. https://foo.com:8000/

        // If is relative
        if (protocol == null && host == null && port == null) {
            if (trimmedUrl.startsWith("//")) {
                //protocol relative URLs, can be served/requested from either http or https. e.g. //cloud.google.com -> https://cloud.google.com
                normalizedURL = baseProtocol + ":" + trimmedUrl;
            }
            else if (trimmedUrl.startsWith("/")) {
//                logger.info("trimmedUrl.startsWith(\"/\")");
                normalizedURL = baseProtocol + "://" + baseHost + ":" + basePort + trimmedUrl;
            }else if (trimmedUrl.startsWith("../")) {
                // "../blubb/123.html" become https://foo.com:8000/bar/blubb/123.html  //TODO second level ../../ and inner level not handled
                logger.debug("trimmedUrl.startsWith(\"../\")");
                String removedBase = removeAfterSecondLastSlashofURL(base);
                normalizedURL = removedBase.length() < baseURLRoot.length() ? baseURLRoot + trimmedUrl.substring(3) : removedBase + trimmedUrl.substring(3);
            } else {
                logger.debug("else loop for trimmedUrl.startsWith()");
//                normalizedURL = seedHttp + "://" + seedHost + ":" + seedPort + removeAfterLastSlashofURL(base) + trimmedUrl;
                String removedBase = removeAfterLastSlashofURL(base);
                normalizedURL = removedBase.length() < baseURLRoot.length() ? baseURLRoot + trimmedUrl : removeAfterLastSlashofURL(base) + trimmedUrl;
            }

            //Handle extra levels in url
            if (normalizedURL.contains("../")) {
                String afterRoot = normalizedURL.substring(baseURLRoot.length());
                normalizedURL = baseURLRoot + handleLevelsInURL(afterRoot);
            }
        } else if (protocol == null) {
            // it's not a relative link, it's broken as it does not contain protocol but contains host or port
            logger.error("broken url found without protocol but contains host and port");
        }
        else if (host != null && port == null) {
            logger.debug("port == null");
            String defaultPort = "http".equals(protocol) ? "80" : "443";
            normalizedURL = protocol + "://" + host + ":" + defaultPort + parsedURL[3];
        } else {
            logger.debug("Else loop for URL normalization. Broken URL without host and port but contains protocol: " + trimmedUrl);
        }

        //Check whether the URL should be filtered out or not (based on the protocol and the extension; see above).
        if (!passedURLFiler(normalizedURL)) return "";

        return normalizedURL;
    }

    private static String handleLevelsInURL(String afterRoot) {
        //e.g. /a/../a/b/../blah.html -> /a/blah.html.
        Stack<String> stack = new Stack<>();
        String[] elements = afterRoot.split("/");
        for (int i = 0; i < elements.length; i++) {
            if (!elements[i].equals("..") || i == elements.length - 1) stack.push(elements[i]);
            else if (!stack.isEmpty()) stack.pop();
        }
        StringBuilder sb = new StringBuilder();
        while (!stack.isEmpty()) {
            sb.insert(0,"/" + stack.pop());
        }
        return sb.length() == 0 ? "" : sb.substring(1);
    }

    private static String removeAfterSecondLastSlashofURL(String seed) {
        int lastSlash = seed.lastIndexOf("/");
        if (lastSlash < 0) {
            return seed;
        }
        int secondLastSlash = seed.substring(0, lastSlash).lastIndexOf("/");
        if (secondLastSlash < 0) {
            return seed.substring(0, lastSlash + 1);
        } else {
            return seed.substring(0, secondLastSlash + 1);
        }
    }

    private static String removeAfterLastSlashofURL(String seed) {
        int lastSlash = seed.lastIndexOf("/");
        if (lastSlash < 0) {
            return seed;
        } else {
            return seed.substring(0, lastSlash + 1);
        }
    }

    private static String urlPatternMatch(String original, String actual) {
        String[] ori = original.split("/");
        String[] act = actual.split("/");
        int actLength = act.length;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ori.length; i++) {
            if (i + actLength < ori.length) {
                sb.append(ori[i]).append("/");
            }
            else if (i + actLength == ori.length) {
                sb.append(act[i]).append("/");
            }
            else{
                logger.warn("invalid url, failed in normalization");
            }
        }
        return sb.toString();
    }
}
