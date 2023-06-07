package cis5550.ranking;

import cis5550.flame.FlameContext;
import cis5550.flame.FlameRDD;
import cis5550.kvs.KVSClient;
import cis5550.kvs.Row;
import cis5550.tools.Hasher;

import static cis5550.webserver.Server.port;

import java.util.Map.Entry;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;



import static cis5550.webserver.Server.get;

public class Ranking {
	public static KVSClient kvsclient;
	static int p; 
	
	public static void main(String[] args) throws Exception {

	if(args.length !=2) {
	    System.err.println("Syntax:Master <port> <kvsmaster>");
	    System.exit(1);
	   }
	//public static void run(FlameContext flameContext, String[] args) throws Exception {
	    try {
		System.out.println("start");
		kvsclient = new KVSClient(args[1]);
		p = Integer.valueOf(args[0]);
		port(p);
	    } catch (Exception e) {
	     //handle the exception
	    System.err.println("An exception occurred: " + e.getMessage());
	    System.exit(1); // exit the program with a non-zero exit code
	    }
	    
	    File file = new File("src/cis5550/ranking/words.txt");
	    ArrayList<String> words_totals = new ArrayList<String>();
	    try(BufferedReader br = new BufferedReader(new FileReader(file))){
	    	String line;
	    	while((line=br.readLine())!=null){
	    	words_totals.add(line);	
	    	}
	    }catch(IOException e) {
	    	e.printStackTrace();
	    }
	    ExecutorService executor = Executors.newFixedThreadPool(100);
	    List<Future<Void>> futures = new ArrayList<Future<Void>>();
	    ExecutorService executor1 = Executors.newFixedThreadPool(50);
	    List<Future<Void>> futures1 = new ArrayList<Future<Void>>();
		///////////////////////////////////////////////////////////////
		//////////////normal search
	
		get("/search/:term",(request,response)->{
		String input_undecode = request.params("term");
		String input = URLDecoder.decode(input_undecode, StandardCharsets.UTF_8);
		////input file which need to be changed
		//String input = "apple to have! railing :to a have andrew.";
	    //String input = "finance";
	    //String input = "Unsupported browser You&apos;re currently using an unsupported browser, which may impact the sites ";
	    //String input = "cook";
	    //String input = "asdhfsa, ajldsfjla";
	    String query = input.replaceAll("\\<[^>]*>","").replaceAll("\\p{Punct}", " ").toLowerCase();
		String[] wordsArray = query.split("\\s+");
		Set<String> uniqueSet = new HashSet<>(Arrays.asList(wordsArray));
		String[] unique_wordsArray = uniqueSet.toArray(new String[uniqueSet.size()]);
		Map<String, ConcurrentHashMap<String, Double>> tftd_map = new ConcurrentHashMap<String, ConcurrentHashMap<String, Double>>();
		Map<String, Double> idf_map = new ConcurrentHashMap<String, Double>();
		Map<String, Double> output_map = new HashMap<String, Double>();
		//Map<String, Double> words_map = null;
		//Row rows;
		int n = kvsclient.count("headers");
		//int frequency = 0;
	    //double idf = 0.0;
	    //double total_words1  =0.0;
	    //double total_count1 = 0.0;
	   // Map<String, Double> count_map = null;
	   // Map<String, Map<String, Double>> info_map = new HashMap<String, Map<String, Double>>();
	    
	    //Map<String, Map<String, Double>> tftd_map1 = new ConcurrentHashMap<String, Map<String, Double>>();
	    Map<String, ConcurrentHashMap<String, Double>> tftd_map1 = new ConcurrentHashMap<String, ConcurrentHashMap<String, Double>>();
	    Map<String, Double> idf_map1 = new ConcurrentHashMap<String, Double>();
		Map<String, Double> output_map1 = new HashMap<String, Double>();
		LinkedHashMap<String, Double> sorted_output_map = new LinkedHashMap<>();
		//Map<String, Double> words_map1 = null;
		StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>\n");
		if(unique_wordsArray.length<100000000) {
	    for (String word: unique_wordsArray) {
	    	//mutithreading for reading words info from kvsclient
	    	Future<Void> future = executor.submit(()->{
	    	if(kvsclient.existsRow("index", word)) {
			Row rows_final1 = kvsclient.getRow("index", word);
	    	String r = rows_final1.get("urls");
				String[] list_url = r.split(",");
				for(int j=0;j<list_url.length;j++) {
					int lastColonIndex = list_url[j].lastIndexOf(":");
					String firstPart = list_url[j].substring(0, lastColonIndex); // extract the first part before ":"
		            String secondPart = list_url[j].substring(lastColonIndex + 1); // extract the second part after ":"
		            Integer frequency_final1 = Integer.parseInt(secondPart);
					//String[] position = secondPart.split(" ");
		            //= new ConcurrentHashMap<String, Double>();
					//words_map1_final1  = tftd_map.get(firstPart);
					//if(words_map1_final1==null) {
					//	words_map1_final1 = new ConcurrentHashMap<String, Double>();
						//words_map1_final1 = new HashMap<String, Double>();
				//		count_map = new HashMap<String, Double>();
					//}
		            ConcurrentHashMap<String, Double> words_map1_final1 = new ConcurrentHashMap<String, Double>();
		            if(tftd_map.get(firstPart)!=null&& !tftd_map.get(firstPart).isEmpty()) {
		            	words_map1_final1 = tftd_map.get(firstPart);
		            }
				//String hashed_firstPart= Hasher.hash(firstPart);
				//System.out.println("hashed_first: "+ hashed_firstPart);
				//if(kvsclient.existsRow("headers", hashed_firstPart)) {
				//	rows = kvsclient.getRow("headers", hashed_firstPart);
				//	String info = rows.get("Context");
					//System.out.println("info: "+info);
				//	int index2 =info.indexOf(", total_words: ");
		          //  String totals = info.substring(index2+15, info.length());
		            
		         //   total_words1 = total_words1+Double.parseDouble(totals);
		        //    total_count1 = total_count1+1;
		       //     count_map.put(word,Double.parseDouble(totals));
		      //      info_map.put(firstPart, count_map); 
		        	//words_map.put(word,frequency/Double.parseDouble(totals));
		        	words_map1_final1.put(word, Math.log10(frequency_final1)+1);
		        	//tftd_map.put(firstPart, words_map);
		        	tftd_map.put(firstPart, words_map1_final1);
			//	}
				//	words_map.put(word, Math.log10(frequency+1));  //not linear growth   
				//	tftd_map.put(firstPart, words_map);  // url, <words wordsfrequency in that url>
				}
				//idf = Math.log10(n / Double.valueOf(list_url.length));           //IDF
				Double idf_final1 = Math.log10(1+((n-Double.valueOf(list_url.length)+0.5) / (Double.valueOf(list_url.length)+0.5)));
				idf_map.put(word, idf_final1);	
	    	}
	    	if(kvsclient.existsRow("urls_index", word)) {
				Row rows_final2 = kvsclient.getRow("urls_index", word);
		    	String r = rows_final2.get("urls");
					String[] list_url = r.split(",");
					for(int j=0;j<list_url.length;j++) {
						String firstPart = list_url[j];
						//words_map1_final2  =  tftd_map1.get(firstPart);
						//if(words_map1_final2==null) {
						//	words_map1_final2 = new ConcurrentHashMap<String, Double>();	
						//}
						ConcurrentHashMap<String, Double> words_map1_final2 = new ConcurrentHashMap<String, Double>();
			            if(tftd_map1.get(firstPart)!=null&& !tftd_map1.get(firstPart).isEmpty()) {
			            	words_map1_final2 = tftd_map1.get(firstPart);
			            }
						words_map1_final2.put(word, Math.log(1.0)+1);  //not linear growth math.log10(1)+1 = 1.0  
						//tftd_map1.put(firstPart, words_map1);  // url, <words wordsfrequency in that url>
						tftd_map1.put(firstPart, words_map1_final2);
					}
					//Double idf_final2 = Math.log10(1+((n-Double.valueOf(list_url.length)+0.5) / (Double.valueOf(list_url.length)+0.5)));
					Double idf_final2 = Math.log10(n / Double.valueOf(list_url.length));
					idf_map1.put(word, idf_final2);	
			}
	    	return null;
	    });
	    	futures.add(future);
	    }
	    //double avg_length =  total_words1/total_count1;
	   //generate output map 
	    for(Future<Void> future:futures) {
	   	try {
            future.get(); // Wait for the task to complete
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
	    }
	    executor.shutdown();
	    
	    try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
	    
	    //calculating scores from normal index table. 
	    double tf_idf_value = 0.0;
		//int index = 0;
		//double k =1.5;
		//double b = 0.75;
		double limit = 0.5;
		int index1 = 0;		
		int index = 0;
		for (Entry<String, ConcurrentHashMap<String, Double>> element : tftd_map.entrySet()) {
			 tf_idf_value = 0.0;
			//Map<String, Double> len_map= info_map.get(element.getKey());
			//System.out.println("element:" + element.getKey());
			for(Entry<String, Double> m :element.getValue().entrySet()) {			
				String w = m.getKey();
				double tf1 = m.getValue();
				double idf1 =idf_map.get(w);
				//tf calculation using BM25: k used for limit the growth of the TF score,  b length of the words L with average of the total words. 
			//	double len = 0.0;
			//	if (len_map.get(w)!=null) {
			//		len = len_map.get(w);
			//	}else {
			//		System.out.println("that happens every time");
			//	}			
				//double tf_adjust = (tf1+(k+1))/ (tf1+k*(1-b+b*(len/avg_length)));
				//tf_idf_value = tf_idf_value + tf_adjust*idf1;
				tf_idf_value = tf_idf_value + tf1*idf1;
			}
		 if(tf_idf_value>limit) {
			 String key = element.getKey();
		 output_map.put(key, tf_idf_value);
		 
		 if(index>100) {
			 break;
		 }
		 index++;	
		}
		}
		//calculating scores from the url_index table
		//tf_idf_value = 0.0;
		//index = 0;
		for (Entry<String, ConcurrentHashMap<String, Double>> element : tftd_map1.entrySet()) {
			tf_idf_value = 0.0;
			for(Entry<String, Double> m :element.getValue().entrySet()) {
				String w = m.getKey();
				double tf1 = m.getValue();
				double idf1 =idf_map1.get(w);
				tf_idf_value = tf_idf_value+ tf1*idf1;
				//tf_idf_value = tf1;
			}
			String key = element.getKey();
			output_map1.put(key, tf_idf_value);
			if(index1>50) {
				 break;
			 }
			 index1++;	
		}
		//part1
		//normalizations 
		Map<String, Double> normalized_outputMap = new HashMap<>();
		double minValue_tf = 0;
		double maxValue_tf = 1;
		double range_tf  = 1;
		if(output_map!=null && !output_map.isEmpty()&& output_map.size()!=1) {
		minValue_tf = Collections.min(output_map.values());
        maxValue_tf = Collections.max(output_map.values());
        range_tf = maxValue_tf - minValue_tf;
        
        for (Map.Entry<String, Double> entry : output_map.entrySet()) {
        	double normalizedValue = (entry.getValue() - minValue_tf) / range_tf;
            normalized_outputMap.put(entry.getKey(),normalizedValue);   
        }
		}else if(output_map.size()==1) {
			for (Map.Entry<String, Double> entry : output_map.entrySet()) {
				normalized_outputMap.put(entry.getKey(), 1.0);   
			}
		}
	    
		//normalization and combination
		Map<String, Double> normalized_outputMap1 = new HashMap<>();
		double minValue_tf1 = 0;
		double maxValue_tf1 = 1;
		double range_tf1  = 1;		
		if(output_map1!=null && !output_map1.isEmpty()&& output_map1.size()!=1) {
		minValue_tf1 = Collections.min(output_map1.values());
        maxValue_tf1 = Collections.max(output_map1.values());
        if(minValue_tf1!=maxValue_tf1) {
        	range_tf1 = maxValue_tf1 - minValue_tf1;
        for (Map.Entry<String, Double> entry : output_map1.entrySet()) {
        	double normalizedValue1 = (entry.getValue() - minValue_tf1) / range_tf1;
            normalized_outputMap1.put(entry.getKey(),normalizedValue1);   
        }}else {
        	for (Map.Entry<String, Double> entry : output_map1.entrySet()) {
                normalized_outputMap1.put(entry.getKey(),0.95);   
            }
        }
		}else if(output_map1.size()==1) {
			for (Map.Entry<String, Double> entry : output_map1.entrySet()) {
				normalized_outputMap1.put(entry.getKey(), 1.0);   
			}
		}		
		
		for (String key : normalized_outputMap.keySet()) {
            if (normalized_outputMap1.containsKey(key)) {
            	normalized_outputMap1.put(key, 0.2*normalized_outputMap.get(key) + 0.8*normalized_outputMap1.get(key));
            } else {
            	normalized_outputMap1.put(key, normalized_outputMap.get(key)*0.9);
            }
		}		
		
		//pagerank features (if there is any pagerank table)
		/*
		Row r1;
        for (Map.Entry<String, Double> entry : normalized_outputMap1.entrySet()) {
        	String url_hashed  = Hasher.hash(entry.getKey());
        	if(kvsclient.existsRow("pagerank", url_hashed)) {
        		r1 = kvsclient.getRow("pagerank", url_hashed);
        		double tfidf_value = entry.getValue();
        		double pr_value =Double.parseDouble(r1.get("rank"));
        		normalized_outputMap1.put(entry.getKey(), tfidf_value*0.9+(1-0.9)*pr_value);  
        	}
        	}
		*/
//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////		
		List<Map.Entry<String, Double>> list = new ArrayList<>(normalized_outputMap1.entrySet());

        // Sort the list using a custom Comparator that compares the values of the entries
        Collections.sort(list, new Comparator<Map.Entry<String, Double>>() {
            public int compare(Map.Entry<String, Double> o1, Map.Entry<String, Double> o2) {
                return Double.compare(o2.getValue(), o1.getValue());
            }
        });
        //LinkedHashMap<String, Double> sorted_output_map = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : list) {
        	sorted_output_map.put(entry.getKey(), entry.getValue());
        }
		//////////////////////Output file test////////////////////////////////////////////////////////////////////////
        Map<String, String> title_map = new ConcurrentHashMap<String, String>();
        Map<String, String> body_map = new ConcurrentHashMap<String, String>();
        
        for (Entry<String, Double> entry : sorted_output_map.entrySet()) {
        	
        	Future<Void> future1 = executor1.submit(()->{
        	String hashed_key = Hasher.hash(entry.getKey());
        	
        	 if(kvsclient.existsRow("headers", hashed_key)) {
                Row rows_headers = kvsclient.getRow("headers", hashed_key);
     			String value = rows_headers.get("Context");
     			int split_index1 = value.indexOf(",title: ");
     			int split_index2 =value.indexOf(", firstWords: ");
     			int split_index3 =value.indexOf(", total_words: ");
                String body = value.substring(split_index2+14, split_index3);
                String title = value.substring(split_index1+8, split_index2);
                title_map.put(entry.getKey(), title.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'",  "&apos;").replace("\"", "&quot;"));
                body_map.put(entry.getKey(), body.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'",  "&apos;").replace("\"", "&quot;"));
        	 }	
        
        	 return null;
	    });
        	
	    	futures1.add(future1);
	    }
	    for(Future<Void> future1:futures1) {
	    try {
            future1.get(); // Wait for the task to complete
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
	    
	    }
	    
	 
	    executor1.shutdown();
	    
	    try {
            executor1.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
		
        //title_map and body_map 
        ///////////////////////////////////////read function/////////////////////////////////////////////////////////////
		
		for (Entry<String, Double> entry : sorted_output_map.entrySet()) {
			DecimalFormat df = new DecimalFormat("#.##");
			String key = entry.getKey();
            Double v = entry.getValue() *100.0;
            String formatted_value = df.format(v);
            
            if(title_map.containsKey(key)) {
            String title_short = title_map.get(key);
            String body_short = body_map.get(key);	
            //make sure the title is not that long
            String[] title_lists = title_short.split(" ");
            if(title_lists.length>30) {
            String[] remainingWords = Arrays.copyOfRange(title_lists, 0, 30);
            title_short= String.join(" ", remainingWords)+"...";
            }
            sb.append("<website>\n<url>");
            sb.append(key.trim().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'",  "&apos;").replace("\"", "&quot;"));
            sb.append("</url>\n");
            sb.append("<score>");
            sb.append(formatted_value);
           // sb.append(v);
            sb.append("</score>\n");
            sb.append("<content>");
            sb.append(body_short.trim());
            sb.append("</content>\n");
            sb.append("<title>");
            sb.append(title_short.trim());
            sb.append("</title>\n");
            sb.append("</website>\n");
            }else {
            	String tt = key.trim().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'",  "&apos;").replace("\"", "&quot;");
            	String truncated_tt = tt.substring(0, Math.min(tt.length(), 30));
            	sb.append("<website>\n<url>");
                sb.append(tt);
                sb.append("</url>\n");
                sb.append("<score>");
                sb.append(formatted_value);
                sb.append("</score>\n");
                sb.append("<content>");
                sb.append("no content");
                sb.append("</content>\n");
                sb.append("<title>");
                //sb.append("no title");
                sb.append(truncated_tt);
                sb.append("</title>\n");
                sb.append("</website>\n");
            }
        }
		}
		sb.append("</root>");
		//System.out.println("xml: "+ sb.toString()); 
	
		return sb.toString();});   

	///////////////////////////////////////////////////////////////////////////add new function: search words 

		get("/findwords/:words",(request,response) ->{			
			String input_undecode = request.params("words");
			String input = URLDecoder.decode(input_undecode, StandardCharsets.UTF_8);
		//	System.out.println("input" + input);
		//	String input = "coods. ssd., aljfoajf  appla";
			String query = input.replaceAll("\\<[^>]*>","").replaceAll("\\p{Punct}", " ").toLowerCase();
			String[] wordsArray = query.split("\\s+");
			Set<String> uniqueSet = new HashSet<>(Arrays.asList(wordsArray));
			String[] unique_wordsArray = uniqueSet.toArray(new String[uniqueSet.size()]);
			//Row rows;
			StringBuilder sb1 = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>\n");
			if (unique_wordsArray.length<8) {
			
			for (String input_words: unique_wordsArray) {
				Map<String, Integer> words_value = new HashMap<>();
			for(String words: words_totals) {
				int same_value = 0;
				int threshold = 2;
				try {
				  same_value = getLevenshteinDistance(words, input_words);
				} catch (Exception e) {
				}
				if(same_value!=0 &&same_value<threshold) {
					 words_value.put(words,same_value);
				}
			    }
				
				if(!words_value.equals(null)&& !words_value.isEmpty()) {
					 List<Map.Entry<String, Integer>> list =
				                new LinkedList<>(words_value.entrySet());
				        Collections.sort(list, Comparator.comparing(Map.Entry::getValue));

				        Map<String, Integer> sortedMap = new LinkedHashMap<>();
				        for (Map.Entry<String, Integer> entry : list) {
				            sortedMap.put(entry.getKey(), entry.getValue());
				        }
				        
				        int loop =3;
				        String recommend_words = "";        
						for (Entry<String, Integer> entry : sortedMap.entrySet()) {
				            String key = entry.getKey();
				            recommend_words = recommend_words+key+ " ";
				            loop = loop-1;
				            if(loop<0) {
				            	break;
				            }
				        }
						sb1.append("<words>");
						sb1.append(input_words+": "+recommend_words.substring(0, recommend_words.length()-1));
				        sb1.append("</words>\n");
				}else {
					sb1.append("<words>");
		            sb1.append(input_words +": "+"Error Inputs");
		            sb1.append("</words>\n");
				}
			//}
			}
			}else {
				sb1.append("<words>");
	            sb1.append("Error Inputs");
	            sb1.append("</words>\n");
			}
			sb1.append("</root>\n"); 
			return sb1.toString();
		 
		}); 
	
	}

	 static int getLevenshteinDistance(String s, String t) {
	        int m = s.length();
	        int n = t.length();
	        int[][] dp = new int[m + 1][n + 1];
	        
	        for (int i = 1; i <= m; i++) {
	            dp[i][0] = i;
	        }
	        for (int j = 1; j <= n; j++) {
	            dp[0][j] = j;
	        }
	        for (int i = 1; i <= m; i++) {
	            for (int j = 1; j <= n; j++) {
	                int substitutionCost = (s.charAt(i - 1) == t.charAt(j - 1)) ? 0 : 1;
	                dp[i][j] = Math.min(dp[i - 1][j] + 1, Math.min(dp[i][j - 1] + 1, dp[i - 1][j - 1] + substitutionCost));
	            }
	        }
	        return dp[m][n];
	    }
	  
}
