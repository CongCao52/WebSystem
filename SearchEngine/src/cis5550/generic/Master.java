package cis5550.generic;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import static cis5550.webserver.Server.*;

public class Master {
	
	//class to store worker's info
	static class WorkerInfo{
		
		private String id;
		private String ip;
		private int port;
		private long lastPingTime;
		
		WorkerInfo(String id, String ip, int port) {
			this.id = id;
			this.ip = ip;
			this.port = port;
			this.setLastPingTime(System.currentTimeMillis());
		}
		
		public void setId(String id) {
			this.id = id;
		}
		
		public String getId() {
			return this.id;
		}
		
		public void setIp(String ip) {
			this.ip = ip;
		}
		
		public String getIp() {
			return this.ip;
		}
		
		public void setPort(int port) {
			this.port = port;
		}
		
		public int getPort() {
			return this.port;
		}

		public long getLastPingTime() {
			return this.lastPingTime;
		}

		public void setLastPingTime(long lastPingTime) {
			this.lastPingTime = lastPingTime;
		}
	}
	
	private static ConcurrentHashMap<String, WorkerInfo> workers = new ConcurrentHashMap<String, WorkerInfo>();
	
	public static ArrayList<String> getWorkers() {
		ArrayList<String> workerss = new ArrayList<String>();
		for (String id : workers.keySet()) {
			workerss.add(workers.get(id).ip + ":" + workers.get(id).port);
		}
		return workerss;
	}
	
	protected static String workerTable() {
		System.out.println("When calling workerTable, workerTable size: " + workers.size());
		StringBuilder sb = new StringBuilder();
		sb.append("<table><tr><th>ID</th><th>IP</th><th>port</th><th>hyperlink</th></tr>");
		for (Map.Entry<String, WorkerInfo> entry : workers.entrySet()) {
			WorkerInfo worker = entry.getValue();
			if (System.currentTimeMillis()- worker.getLastPingTime() < 15000) {
				sb.append("<tr>");
				sb.append("<td>" + worker.getId() + "</td>");
				sb.append("<td>" + worker.getIp() + "</td>");
				sb.append("<td>" + worker.getPort() + "</td>");
				String hyperlink = "\"http://" + worker.getIp() + ":" + worker.getPort() + "/\"";
 				sb.append("<td><a href=" + hyperlink + ">Link to KVS worker</a></td>");
 				sb.append("</tr>");
			}
		}
		sb.append("<table>");
		System.out.println(sb.toString());
		return sb.toString();
	}
	
	protected static void registerRoutes() {
		get("/ping", (req,res) -> { 
			String ip = req.ip();
			String id = req.queryParams("id");
			int port = -1;
			try {
				port = Integer.valueOf(req.queryParams("port"));
			} catch (Exception e) {
				e.printStackTrace();
			}
			if (id == null || port == -1) {
				res.status(400, "Bad Request");
				res.body("Bad Request");
			} else {
				if (workers.get(id) == null) {
					WorkerInfo newWorker = new WorkerInfo(id, ip, port);
					workers.put(id, newWorker);
				} else {
					workers.get(id).setIp(ip);
					workers.get(id).setLastPingTime(System.currentTimeMillis());
				}
				res.status(200, "OK");
				res.body("OK");
			}
			return null;});
		get("/workers", (req,res) -> {
			res.type("text/plain");
			res.status(200, "OK");
			StringBuilder sb = new StringBuilder();
			int num = 0;
			for (Map.Entry<String, WorkerInfo> entry : workers.entrySet()) {
				WorkerInfo worker = entry.getValue();
				if (System.currentTimeMillis() - worker.getLastPingTime() < 15000) {
					num++;
					sb.append(worker.getId() + "," + worker.getIp() + ":" + worker.getPort() + "\n");
				}
			}
			res.body(String.valueOf(num) + "\n" + sb.toString());
			return null;});
		
		
	}
}
