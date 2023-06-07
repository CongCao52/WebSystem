package cis5550.flame;

import java.util.*;
import java.io.*;

import static cis5550.webserver.Server.*;
import cis5550.tools.Hasher;
import cis5550.tools.Serializer;
import cis5550.flame.FlameContext.RowToString;
import cis5550.flame.FlamePairRDD.PairToPairIterable;
import cis5550.flame.FlamePairRDD.PairToStringIterable;
import cis5550.flame.FlamePairRDD.TwoStringsToString;
import cis5550.flame.FlameRDD.IteratorToIterator;
import cis5550.flame.FlameRDD.StringToBoolean;
import cis5550.flame.FlameRDD.StringToIterable;
import cis5550.flame.FlameRDD.StringToPair;
import cis5550.flame.FlameRDD.StringToPairIterable;
import cis5550.kvs.*;

class Worker extends cis5550.generic.Worker {

	public static void main(String args[]) {
    if (args.length != 2) {
    	System.err.println("Syntax: Worker <port> <masterIP:port>");
    	System.exit(1);
    }

    int port = Integer.parseInt(args[0]);
    String server = args[1];
	  startPingThread(server, ""+port, port);
    final File myJAR = new File("__worker"+port+"-current.jar");

  	port(port);

    post("/useJAR", (request,response) -> {
      FileOutputStream fos = new FileOutputStream(myJAR);
      fos.write(request.bodyAsBytes());
      fos.close();
      return "OK";
    });
    
    post("/rdd/flatMap", (request,response) -> {
    	String inputTable = request.queryParams("Input");
    	String outputTable = request.queryParams("output");
    	String kvs = request.queryParams("kvs");
    	String start = request.queryParams("start");
    	String end = request.queryParams("end");
    	
    	StringToIterable lambda = (StringToIterable) Serializer.byteArrayToObject(request.bodyAsBytes(), myJAR);
    	Master.kvs = new KVSClient(kvs);
    	if (start.equals("null")) {
    		start = null;
    	}
    	if (end.equals("null")) {
    		end = null;
    	}
    	Iterator<Row> rows = Master.kvs.scan(inputTable, start, end);
    	int sequence = 0;
    	while (rows.hasNext()) {
    		Row row = rows.next();
    		Iterable<String> strings = lambda.op(row.get("value"));
    		if (strings != null) {
    			for (String string : strings) {
        			String rowKey = Hasher.hash(server+port+sequence);
        			sequence++;
					Master.kvs.put(outputTable, rowKey, "value", string);
        		}
    		}    		
    	}   	
        return "OK";
      });
    
    post("/rdd/flatMapPair", (request,response) -> {
    	String inputTable = request.queryParams("Input");
    	String outputTable = request.queryParams("output");
    	String kvs = request.queryParams("kvs");
    	String start = request.queryParams("start");
    	String end = request.queryParams("end");
    	
    	PairToStringIterable lambda = (PairToStringIterable) Serializer.byteArrayToObject(request.bodyAsBytes(), myJAR);
		Master.kvs = new KVSClient(kvs);
    	if (start.equals("null")) {
    		start = null;
    	}
    	if (end.equals("null")) {
    		end = null;
    	}
    	Iterator<Row> rows = Master.kvs.scan(inputTable, start, end);
    	int sequence = 0;
    	while (rows.hasNext()) {
    		Row row = rows.next();
    		for (String col : row.columns()) {
    			Iterator<String> values = lambda.op(new FlamePair(row.key(),row.get(col))).iterator();
    			while (values.hasNext()) {
    				String value = values.next();
    				String rowKey = Hasher.hash(server+port+sequence);
        			sequence++;
					Master.kvs.put(outputTable, rowKey, "value", value);
    			}   			
    		}   		    		
    	}   	
        return "OK";
      });
    
    post("/rdd/mapToPair", (request,response) -> {
    	String inputTable = request.queryParams("Input");
    	String outputTable = request.queryParams("output");
    	String kvs = request.queryParams("kvs");
    	String start = request.queryParams("start");
    	String end = request.queryParams("end");
    	
    	StringToPair lambda = (StringToPair) Serializer.byteArrayToObject(request.bodyAsBytes(), myJAR);
		Master.kvs = new KVSClient(kvs);
    	if (start.equals("null")) {
    		start = null;
    	}
    	if (end.equals("null")) {
    		end = null;
    	}
    	Iterator<Row> rows = Master.kvs.scan(inputTable, start, end);
    	while (rows.hasNext()) {
    		Row row = rows.next();
    		FlamePair pair = lambda.op(row.get("value"));
    		if (pair != null) {
				Master.kvs.put(outputTable, pair._1(), row.key(), pair._2());
    		}    		
    	}   	
        return "OK";
      });
    
    post("/rdd/foldByKey", (request,response) -> {
    	String inputTable = request.queryParams("Input");
    	String outputTable = request.queryParams("output");
    	String kvs = request.queryParams("kvs");
    	String start = request.queryParams("start");
    	String end = request.queryParams("end");
    	String zeroElement = request.queryParams("zeroElement");
    	
    	TwoStringsToString lambda = (TwoStringsToString) Serializer.byteArrayToObject(request.bodyAsBytes(), myJAR);
		Master.kvs = new KVSClient(kvs);
    	if (start.equals("null")) {
    		start = null;
    	}
    	if (end.equals("null")) {
    		end = null;
    	}
    	Iterator<Row> rows = Master.kvs.scan(inputTable, start, end);
    	while (rows.hasNext()) {
    		Row row = rows.next();
    		Set<String> columns = row.columns();
    		String accumulator = zeroElement;
    		for (String column : columns) {
    			accumulator = lambda.op(row.get(column),accumulator);
    		}
			Master.kvs.put(outputTable, row.key(),"value", accumulator);
    	}   	
        return "OK";
      });
    
    
    post("/fromTable", (request,response) -> {
    	String inputTable = request.queryParams("Input");
    	String outputTable = request.queryParams("output");
    	String kvs = request.queryParams("kvs");
    	String start = request.queryParams("start");
    	String end = request.queryParams("end");
    	
    	RowToString lambda = (RowToString) Serializer.byteArrayToObject(request.bodyAsBytes(), myJAR);
		Master.kvs = new KVSClient(kvs);
    	if (start.equals("null")) {
    		start = null;
    	}
    	if (end.equals("null")) {
    		end = null;
    	}
    	Iterator<Row> rows = Master.kvs.scan(inputTable, start, end);
    	while (rows.hasNext()) {
    		Row row = rows.next();
    		String value = lambda.op(row);
			Master.kvs.put(outputTable, row.key(),"value", value);
    	}   	
        return "OK";
      });

    post("/rdd/flatMapToPair", (request,response) -> {
    	String inputTable = request.queryParams("Input");
    	String outputTable = request.queryParams("output");
    	String kvs = request.queryParams("kvs");
    	String start = request.queryParams("start");
    	String end = request.queryParams("end");
    	
    	StringToPairIterable lambda = (StringToPairIterable) Serializer.byteArrayToObject(request.bodyAsBytes(), myJAR);
		Master.kvs = new KVSClient(kvs);
    	if (start.equals("null")) {
    		start = null;
    	}
    	if (end.equals("null")) {
    		end = null;
    	}
    	Iterator<Row> rows = Master.kvs.scan(inputTable, start, end);
    	System.out.println(start + " " + end);
    	int sequence = 0;
    	while (rows.hasNext()) {
    		Row row = rows.next();
    		if (row == null) {
    			continue;
    		}
    		Iterable<FlamePair> pairs = lambda.op(row.get("value"));
    		if (pairs != null) {
    			for (FlamePair pair : pairs) {
        			String colKey = Hasher.hash(server+port+sequence);
        			sequence++;
					Master.kvs.put(outputTable, pair._1(), colKey, pair._2());
        			System.out.println(pair._1() + " " + pair._2());
        		}
    		}    		
    	}   	
        return "OK";
      });
    
    post("/rdd/flatMaptoPairPair", (request,response) -> {
    	String inputTable = request.queryParams("Input");
    	String outputTable = request.queryParams("output");
    	String kvs = request.queryParams("kvs");
    	String start = request.queryParams("start");
    	String end = request.queryParams("end");
    	
    	PairToPairIterable lambda = (PairToPairIterable) Serializer.byteArrayToObject(request.bodyAsBytes(), myJAR);
		Master.kvs = new KVSClient(kvs);
    	if (start.equals("null")) {
    		start = null;
    	}
    	if (end.equals("null")) {
    		end = null;
    	}
    	Iterator<Row> rows = Master.kvs.scan(inputTable, start, end);
    	System.out.println(start + " " + end);
    	int sequence = 0;
    	while (rows.hasNext()) {
    		Row row = rows.next();
    		for (String col : row.columns()) {
    			Iterator<FlamePair> pairs = lambda.op(new FlamePair(row.key(),row.get(col))).iterator();
    			while (pairs.hasNext()) {
    				FlamePair pair = pairs.next();
    				String colKey = Hasher.hash(server+port+sequence);
        			sequence++;
					Master.kvs.put(outputTable, pair._1(), colKey, pair._2());
    			}
    		}   		   		
    	}   	
        return "OK";
      });
    
    post("/rdd/join", (request,response) -> {
    	String inputTable = request.queryParams("Input");
    	String outputTable = request.queryParams("output");
    	String kvs = request.queryParams("kvs");
    	String start = request.queryParams("start");
    	String end = request.queryParams("end");
    	
    	FlamePairRDDImpl other = new FlamePairRDDImpl(request.body());
		Master.kvs = new KVSClient(kvs);
    	if (start.equals("null")) {
    		start = null;
    	}
    	if (end.equals("null")) {
    		end = null;
    	}
    	Iterator<Row> rows1 = Master.kvs.scan(inputTable, start, end);
    	  	
    	while (rows1.hasNext()) {
    		Row row1 = rows1.next();
    		Iterator<Row> rows2 = Master.kvs.scan(other.getTableName());
    		while (rows2.hasNext()) {
    			Row row2 = rows2.next();
    			if (row1.key().equals(row2.key())) {
    				for (String col1 : row1.columns()) {
    					for (String col2 : row2.columns()) {
							Master.kvs.put(outputTable, row1.key(), Hasher.hash(col1) + "#" + Hasher.hash(col2), row1.get(col1) + "," + row2.get(col2));
    					}
    				}
    			}
    			
    		}
    	}   	   	
        return "OK";
      });
    
    post("/rdd/filter", (request,response) -> {
    	String inputTable = request.queryParams("Input");
    	String outputTable = request.queryParams("output");
    	String kvs = request.queryParams("kvs");
    	String start = request.queryParams("start");
    	String end = request.queryParams("end");
    	
    	StringToBoolean lambda = (StringToBoolean) Serializer.byteArrayToObject(request.bodyAsBytes(), myJAR);
		Master.kvs = new KVSClient(kvs);
    	if (start.equals("null")) {
    		start = null;
    	}
    	if (end.equals("null")) {
    		end = null;
    	}
    	Iterator<Row> rows = Master.kvs.scan(inputTable, start, end);
    	while (rows.hasNext()) {
    		Row row = rows.next();
    		if (lambda.op(row.get("value"))) {
				Master.kvs.put(outputTable, row.key(), "value", row.get("value"));
    		}
    	}   	
        return "OK";
      });
    
    post("/rdd/mapPartitions", (request,response) -> {
    	String inputTable = request.queryParams("Input");
    	String outputTable = request.queryParams("output");
    	String kvs = request.queryParams("kvs");
    	String start = request.queryParams("start");
    	String end = request.queryParams("end");
    	
    	IteratorToIterator lambda = (IteratorToIterator) Serializer.byteArrayToObject(request.bodyAsBytes(), myJAR);
		Master.kvs = new KVSClient(kvs);
    	if (start.equals("null")) {
    		start = null;
    	}
    	if (end.equals("null")) {
    		end = null;
    	}
    	int sequence = 0;
    	ArrayList<String> values = new ArrayList<String>();
    	Iterator<Row> rows = Master.kvs.scan(inputTable, start, end);
    	while (rows.hasNext()) {
    		Row row = rows.next();
    		values.add(row.get("value"));
    	}
    	Iterator<String> newValues = lambda.op(values.iterator());
    	while (newValues.hasNext()) {
			Master.kvs.put(outputTable,Hasher.hash(""+System.currentTimeMillis()+sequence), "value", newValues.next());
    	}
        return "OK";
      });
	}
}
