package cis5550.flame;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.Vector;

import cis5550.flame.FlamePairRDD.TwoStringsToString;
import cis5550.kvs.KVSClient;
import cis5550.kvs.Row;
import cis5550.tools.Hasher;
import cis5550.tools.Serializer;

public class FlameRDDImpl implements FlameRDD {
	
	private String tableName;
	KVSClient kvs = Master.kvs;
	
	FlameRDDImpl(String tableName) {
		this.tableName = tableName;

	}

	@Override
	public List<String> collect() throws Exception {
		Iterator<Row> iterator = kvs.scan(tableName);
		List<String> values = new ArrayList<String>();
		while(iterator.hasNext()) {
			String value = iterator.next().get("value");
			values.add(value);
		}
		return values;
	}

	@Override
	public FlameRDD flatMap(StringToIterable lambda) throws Exception {
		byte[] function = Serializer.objectToByteArray(lambda);
		String outputTable = FlameContextImpl.invokeOperation("/rdd/flatMap", function, kvs, tableName);
		FlameRDD result = new FlameRDDImpl(outputTable);
		return result;
	}

	@Override
	public FlamePairRDD mapToPair(StringToPair lambda) throws Exception {
		byte[] function = Serializer.objectToByteArray(lambda);
		String outputTable = FlameContextImpl.invokeOperation("/rdd/mapToPair", function, kvs, tableName);
		FlamePairRDD result = new FlamePairRDDImpl(outputTable);
		return result;
	}

	@Override
	public FlameRDD intersection(FlameRDD r) throws Exception {
		Random rand = new Random();
		int num = rand.nextInt(1000);
		String tableID = "" + System.currentTimeMillis() + num;
		FlameRDD result = new FlameRDDImpl(tableID);
		Set<String> content1 = new HashSet<String>(this.collect());
		Set<String> content2 = new HashSet<String>(r.collect());
		content1.retainAll(content2);
		ArrayList<String> theContent = new ArrayList<String>(content1);
		for (int i = 0; i < theContent.size(); i++) {
			kvs.put(tableID, Hasher.hash(""+i), "value", theContent.get(i));
		}
		return result;
	}

	@Override
	public FlameRDD sample(double f) throws Exception {
		Random rand = new Random();
		int num = rand.nextInt(1000);
		String tableID = "" + System.currentTimeMillis() + num;
		FlameRDD result = new FlameRDDImpl(tableID);
		int i = 0;
		for (String string : this.collect()) {
			if (rand.nextDouble()<f) {
				kvs.put(tableID, Hasher.hash(""+i), "value", string);
				i++;
			}
		}
		return result;
	}


	@Override
	public int count() throws Exception {
		return kvs.count(tableName);
	}

	@Override
	public void saveAsTable(String tableNameArg) throws Exception {
		kvs.rename(tableName, tableNameArg);
		this.tableName = tableNameArg;		
	}

	@Override
	public FlameRDD distinct() throws Exception {
		String outputTable = "" + System.currentTimeMillis();
		Iterator<Row> rows = kvs.scan(tableName);
		while (rows.hasNext()) {
			Row row = rows.next();
			kvs.put(outputTable, row.get("value"), "value", row.get("value"));
		}
		FlameRDDImpl result = new FlameRDDImpl(outputTable);
		return result;
	}

	@Override
	public Vector<String> take(int num) throws Exception {
		Vector<String> content = new Vector<String>();
		Iterator<Row> rows = kvs.scan(tableName);
		while (rows.hasNext() && content.size() < num) {
			content.add(rows.next().get("value"));
		}
		return content;
	}

	@Override
	public String fold(String zeroElement, TwoStringsToString lambda) throws Exception {
		byte[] function = Serializer.objectToByteArray(lambda);
		String table = FlameContextImpl.invokeOperation("/rdd/foldByKey", function, kvs, tableName,zeroElement);
		Iterator<Row> rows = kvs.scan(table);
		String accumulator = zeroElement;
		while (rows.hasNext()) {
			accumulator = lambda.op(rows.next().get("value"),accumulator);
		}
		return accumulator;
	}

	@Override
	public FlamePairRDD flatMapToPair(StringToPairIterable lambda) throws Exception {
		byte[] function = Serializer.objectToByteArray(lambda);
		String outputTable = FlameContextImpl.invokeOperation("/rdd/flatMapToPair", function, kvs, tableName);
		FlamePairRDDImpl result = new FlamePairRDDImpl(outputTable);
		return result;
	}

	@Override
	public FlameRDD filter(StringToBoolean lambda) throws Exception {
		byte[] function = Serializer.objectToByteArray(lambda);
		String outputTable = FlameContextImpl.invokeOperation("/rdd/filter", function, kvs, tableName);
		FlameRDDImpl result = new FlameRDDImpl(outputTable);
		return result;
	}

	@Override
	public FlameRDD mapPartitions(IteratorToIterator lambda) throws Exception {
		byte[] function = Serializer.objectToByteArray(lambda);
		String outputTable = FlameContextImpl.invokeOperation("/rdd/mapPartitions", function, kvs, tableName);
		FlameRDDImpl result = new FlameRDDImpl(outputTable);
		return result;
	}

	@Override
	public FlamePairRDD groupBy(StringToString lambda) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

}
