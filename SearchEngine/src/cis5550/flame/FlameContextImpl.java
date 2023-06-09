package cis5550.flame;

import java.io.Serializable;
import java.util.List;
import java.util.Vector;

import cis5550.kvs.KVSClient;
import cis5550.tools.HTTP;
import cis5550.tools.Hasher;
import cis5550.tools.Partitioner;
import cis5550.tools.Serializer;
import cis5550.tools.Partitioner.Partition;

public class FlameContextImpl implements FlameContext, Serializable {
	
	private static int keyRangesPerWorker = 1;

	private StringBuilder sb = new StringBuilder();
	public static int sequence = 0;
	String jarName;

	FlameContextImpl(String jarName) {
		this.jarName = jarName;
	}

	@Override
	public KVSClient getKVS() {
		return Master.kvs;
	}

	@Override
	public void output(String s) {
		sb.append(s);
	}

	@Override
	public FlameRDD parallelize(List<String> list) throws Exception {
		String tableID = "" + System.currentTimeMillis() + sequence;
		sequence++;
		for (int i = 0; i < list.size(); i++) {
			this.getKVS().put(tableID, Hasher.hash(String.valueOf(i)), "value", list.get(i));
		}
		FlameRDDImpl rdd = new FlameRDDImpl(tableID);

		return rdd;
	}

	public String getOutput() {
		String output = sb.toString();
		if (output.isEmpty()) {
			return "No output generated by the job.";
		} else {
			return output;
		}
	}

	public static String invokeOperation(String opName, byte[] function, KVSClient kvs, String inputTable, String zeroElement) throws Exception {
		String outputTable = "" + System.currentTimeMillis() + sequence;
		sequence++;
		int KVSWorkerNum = kvs.numWorkers();
		if (KVSWorkerNum < 1) {
			throw new Exception("No KVSWorker.");
		}
		Partitioner partitioner = new Partitioner();
		if (KVSWorkerNum == 1) {
			partitioner.addKVSWorker(kvs.getWorkerAddress(0), null, null);
		} else {
			for (int i = 0; i < KVSWorkerNum - 1; i++) {
				partitioner.addKVSWorker(kvs.getWorkerAddress(i), kvs.getWorkerID(i),
						kvs.getWorkerID(i + 1));
			}
			partitioner.addKVSWorker(kvs.getWorkerAddress(KVSWorkerNum - 1),
					kvs.getWorkerID(KVSWorkerNum - 1), null);
			partitioner.addKVSWorker(kvs.getWorkerAddress(KVSWorkerNum - 1), null,
					kvs.getWorkerID(0));
		}

		for (String worker : Master.getWorkers()) {
			for (int i = 0; i < keyRangesPerWorker; i++) {
				partitioner.addFlameWorker(worker);
			}
		}

		Vector<Partition> partitions = partitioner.assignPartitions();

		Thread threads[] = new Thread[partitions.size()];
		String results[] = new String[partitions.size()];
		Integer statusCode[] = new Integer[Master.getWorkers().size()];
		for (int i = 0; i < partitions.size(); i++) {
			StringBuilder sb = new StringBuilder();
			sb.append("http://" + partitions.get(i).assignedFlameWorker + opName + "?");
			sb.append("Input="+inputTable + "&");
			sb.append("output=" + outputTable + "&");
			sb.append("kvs="+kvs.getMaster()+"&");
			sb.append("start="+partitions.get(i).fromKey+"&");
			if (zeroElement != null) {
				sb.append("zeroElement="+zeroElement+"&");
			}
			sb.append("end=" + partitions.get(i).toKeyExclusive);
			
			final String url = sb.toString();
			final int j = i;
			threads[i] = new Thread("operation inform #" + (i + 1)) {
				
				@Override
				public void run() {
					try {
						cis5550.tools.HTTP.Response res = HTTP.doRequest("POST", url, function);
						statusCode[j] = res.statusCode();
						if (res.statusCode() != 200) {
							throw new Exception("Request return status code other than 200.");							
						}
						results[j] = new String(res.body());
					} catch (Exception e) {
						results[j] = "Exception: " + e;
						e.printStackTrace();
					}
				}
			};
			threads[i].start();
		}

		// Wait for all the uploads to finish

		for (int i = 0; i < threads.length; i++) {
			try {
				threads[i].join();
			} catch (InterruptedException ie) {
				throw ie;
			}
		}
		
		for (int i : statusCode) {
			if (i != 200) {
				throw new Exception("Not all thread gets 200.");
			}
		}
		return outputTable;

	}
	
	public static String invokeOperation(String opName, byte[] function, KVSClient kvs, String inputTable) throws Exception {
		String outputTable = invokeOperation(opName, function, kvs, inputTable, null);
		return outputTable;
	}

	@Override
	public FlameRDD fromTable(String tableName, RowToString lambda) throws Exception {
		byte[] function = Serializer.objectToByteArray(lambda);
		String outputTable = FlameContextImpl.invokeOperation("/fromTable", function, this.getKVS(), tableName);
		FlameRDD result = new FlameRDDImpl(outputTable);
		return result;
	}

	@Override
	public void setConcurrencyLevel(int keyRangesPerWorker) {
		FlameContextImpl.keyRangesPerWorker = keyRangesPerWorker;		
	}

}
