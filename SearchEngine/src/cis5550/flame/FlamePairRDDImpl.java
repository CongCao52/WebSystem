package cis5550.flame;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import cis5550.kvs.KVSClient;
import cis5550.kvs.Row;
import cis5550.tools.Serializer;

public class FlamePairRDDImpl implements FlamePairRDD {
	
	private String tableName;
	KVSClient kvs = Master.kvs;
	
	FlamePairRDDImpl(String tableName) {
		this.tableName = tableName;

	}
	
	@Override
	public String getTableName() {
		return this.tableName;
	}

	@Override
	public List<FlamePair> collect() throws Exception {
		Iterator<Row> iterator = kvs.scan(tableName);
		List<FlamePair> pairs = new ArrayList<FlamePair>();
		while(iterator.hasNext()) {
			Row row = iterator.next();
			Set<String> columns = row.columns();
			for (String column : columns) {
				String value = row.get(column);
				FlamePair pair = new FlamePair(row.key(),value);
				pairs.add(pair);
			}
		}
		return pairs;
	}

	@Override
	public FlamePairRDD foldByKey(String zeroElement, TwoStringsToString lambda) throws Exception {
		byte[] function = Serializer.objectToByteArray(lambda);
		String outputTable = FlameContextImpl.invokeOperation("/rdd/foldByKey", function, kvs, tableName, zeroElement);
		FlamePairRDD result = new FlamePairRDDImpl(outputTable);
		return result;
	}

	@Override
	public void saveAsTable(String tableNameArg) throws Exception {
		kvs.rename(tableName, tableNameArg);
		this.tableName = tableNameArg;
		
	}

	@Override
	public FlameRDD flatMap(PairToStringIterable lambda) throws Exception {
		byte[] function = Serializer.objectToByteArray(lambda);
		String outputTable = FlameContextImpl.invokeOperation("/rdd/flatMapPair", function, kvs, tableName);
		FlameRDD result = new FlameRDDImpl(outputTable);
		return result;
	}

	@Override
	public FlamePairRDD flatMapToPair(PairToPairIterable lambda) throws Exception {
		byte[] function = Serializer.objectToByteArray(lambda);
		String outputTable = FlameContextImpl.invokeOperation("/rdd/flatMaptoPairPair", function, kvs, tableName);
		FlamePairRDD result = new FlamePairRDDImpl(outputTable);
		return result;
	}

	@Override
	public FlamePairRDD join(FlamePairRDD other) throws Exception {
		String tableName2 = other.getTableName();
		byte[] bytes = tableName2.getBytes();
		String outputTable = FlameContextImpl.invokeOperation("/rdd/join", bytes, kvs, tableName);
		FlamePairRDD result = new FlamePairRDDImpl(outputTable);
		return result;
		
	}

	@Override
	public FlamePairRDD cogroup(FlamePairRDD other) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}
	

}
