package cis5550.kvs;

import static cis5550.webserver.Server.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

public class Worker extends cis5550.generic.Worker {

	static ConcurrentHashMap<String, ConcurrentHashMap<String, Row>> data = new ConcurrentHashMap<String, ConcurrentHashMap<String, Row>>();
	static TreeSet<String> pTable = new TreeSet<String>();
	static String directory = null;

	public static void putRow(String table, Row row) {
		//System.out.println("putRow called.");
		ConcurrentHashMap<String, Row> requestTable = data.get(table);
		if (requestTable == null) {
			requestTable = new ConcurrentHashMap<String, Row>();
			data.put(table, requestTable);
			new File(directory, table + ".table");
		}
		byte[] rowContent = Arrays.copyOf(row.toByteArray(), row.toByteArray().length + 1);
		rowContent[rowContent.length - 1] = 10;

		try (RandomAccessFile file = new RandomAccessFile(new File(directory, table + ".table"), "rw")) {
			if (pTable.contains(table)) {
				Row pRow = new Row(row.key());
				pRow.put("pos", String.valueOf(file.length()));
				requestTable.put(row.key(), pRow);
			} else {
				requestTable.put(row.key(), row);
			}
			file.seek(file.length());
			file.write(rowContent);
			file.getFD().sync();
			//System.out.println("write: " + rowContent + " to " + table);
		} catch (Exception e) {
			return;
		}
	}

	public static Row getRow(String table, String row) {

		ConcurrentHashMap<String, Row> requestTable = data.get(table);

		if (requestTable == null) {
			return null;
		} else {
			Row requestRow = requestTable.get(row);
			if (pTable.contains(table)) {
				try (RandomAccessFile file = new RandomAccessFile(new File(directory, table + ".table"), "r")) {
					file.seek(Long.valueOf(requestRow.get("pos")));
					requestRow = Row.readFrom(file);
				} catch (Exception e) {
					return null;
				}
			}
			return requestRow;
		}

	}

	public static void main(String args[]) {

		if (args.length != 3) {
			return;
		}

		int workerPort = -1;

		try {
			workerPort = Integer.valueOf(args[0]);
			port(workerPort);
		} catch (Exception e) {
			return;
		}

		String masterIDandPort = args[2];

		String workerID = null;

		directory = args[1];
		
		File dire = new File(directory);
		if (!dire.exists()) {
		    dire.mkdirs();
		}

		File idFile = new File(directory, "id");
		if (idFile.exists()) {

			try (BufferedReader reader = new BufferedReader(new FileReader(idFile))) {
				workerID = reader.readLine();
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}

		} else {
			String chars = "abcdefghijklmnopqrstuvwxyz";
			StringBuilder idBuilder = new StringBuilder();
			Random rnd = new Random();
			while (idBuilder.length() < 5) {
				int index = (int) (rnd.nextFloat() * chars.length());
				idBuilder.append(chars.charAt(index));
			}
			workerID = idBuilder.toString();

			try (BufferedWriter writer = new BufferedWriter(new FileWriter(idFile))) {
				writer.write(workerID);
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
		}

		startPingThread(masterIDandPort, workerID, workerPort);

		FilenameFilter filter = (dir, name) -> name.endsWith(".table");
		File dir = new File(directory);
		File[] files = dir.listFiles(filter);
		for (File file : files) {
			try (RandomAccessFile r = new RandomAccessFile(file, "r")) {
				ConcurrentHashMap<String, Row> table = new ConcurrentHashMap<String, Row>();
				String tableName = file.getName().substring(0, file.getName().lastIndexOf('.'));
				data.put(tableName, table);
				System.out.println();
				pTable.add(tableName);
				while (true) {
					long off = r.getFilePointer();
					Row row = Row.readFrom(r);
					if (row == null) {
						break;
					}
					if (table.get(row.key()) == null) {
						table.put(row.key(), new Row(row.key));
					}
					table.get(row.key()).put("pos", String.valueOf(off));
				}
			} catch (Exception e) {
				e.printStackTrace();
				return;
			}

		}
		System.out.println(data.keySet());

		put("/data/:table/:row/:column", (req, res) -> {

			synchronized (data) {
				String table = req.params("table");
				String row = req.params("row");
				String column = req.params("column");
				String ifcolumn = req.queryParams("ifcolumn");
				String equals = req.queryParams("equals");

				Row requestRow = getRow(table, row);

				if (requestRow != null) {
					System.out.println("not null");
					if (ifcolumn != null && equals != null) {
						if (requestRow.values.keySet().contains(ifcolumn) && requestRow.get(ifcolumn) != equals) {
							res.status(200, "OK");
							res.body("FAIL");
							return null;
						}
					}
					requestRow.put(column, req.bodyAsBytes());
					putRow(table, requestRow);
				} else {
					System.out.println("null");
					requestRow = new Row(row);
					requestRow.put(column, req.bodyAsBytes());
					putRow(table, requestRow);
				}

				res.status(200, "OK");
				res.body("OK");

				return null;
			}
		});

		get("/data/:table/:row/:column", (req, res) -> {
			synchronized (data) {
				String table = req.params("table");
				String row = req.params("row");
				String column = req.params("column");

				if (table == null || row == null || column == null) {
					res.status(404, "Not Found");
					res.body("Not Found1");
				} else {
					if (data.get(table) == null) {
						res.status(404, "Not Found");
						res.body("Not Found2");
					} else {
						ConcurrentHashMap<String, Row> requestTable = data.get(table);
						if (requestTable.get(row) == null) {
							res.status(404, "Not Found");
							res.body("Not Found3");
						} else {
							Row requestRow = getRow(table, row);
							if (requestRow == null || requestRow.get(column) == null) {
								res.status(404, "Not Found");
								res.body("Not Found4");
							} else {
								res.status(200, "OK");
								res.bodyAsBytes(requestRow.getBytes(column));
							}
						}
					}
				}
				return null;
			}
		});

		get("/", (req, res) -> {
			synchronized (data) {
				res.type("text/html");
				res.status(200, "OK");
				StringBuilder sb = new StringBuilder();
				sb.append(
						"<html><head><title>List of Tables</title></head><body><h1>List of Tables<h1><p>This is the list of tables</p>");
				sb.append("<table><tr><th>Table Name</th><th># of Keys</th><th>persistent/in-memory</th></tr>");
				for (Entry<String, ConcurrentHashMap<String, Row>> entry : data.entrySet()) {
					sb.append("<tr>");
					String hyperlink = "\"" + req.url() + "view/" + entry.getKey() + "\"";
					sb.append("<td><a href=" + hyperlink + ">" + entry.getKey() + "</a></td>");
					sb.append("<td>" + entry.getValue().size() + "</td>");
					if (pTable.contains(entry.getKey())) {
						sb.append("<td>persistent</td>");
					} else {
						sb.append("<td>in-memory</td>");
					}
					sb.append("</tr>");
				}
				sb.append("<table>");
				sb.append("</body></html>\r\n");
				String content = sb.toString();
				// System.out.println(content);
				res.body(content);
				return null;
			}
		});

		get("/view/:table", (req, res) -> {

			synchronized (data) {
				System.out.println("Start.");
				String table = req.params("table");
				String start = req.queryParams("fromRow");
				
				if (data.get(table) == null) {
					res.type("text/plain");
					res.status(404, "Not Found");
					res.body("Not Found");
					return null;
				}

				res.type("text/html");
				res.status(200, "OK");
				System.out.println("success.");

				ConcurrentHashMap<String, Row> requestTable = data.get(table);
				ArrayList<String> keys = new ArrayList<String>(requestTable.keySet());
				Collections.sort(keys);
				int keyNum = keys.size();
				
				int count = 0;
				ArrayList<Integer> index = new ArrayList<Integer>();
				for (int i = 0; i < keyNum; i++) {
					if (start == null || keys.get(i).compareTo(start) >=0) {
						index.add(i);
						count ++;
					}				
					if (count >= 10) {
						break;
					}
				}
				boolean end = index.size() == 0 || index.get(index.size()-1) >= (keys.size()-1);
				
				TreeSet<String> columnName = new TreeSet<String>();
				for (int i : index) {
					columnName.addAll(requestTable.get(keys.get(i)).columns());
				}

				StringBuilder sb = new StringBuilder();
				sb.append("<html><head><title>" + table + "</title></head><body><h1>" + table + "<h1><p></p>");
				sb.append("<table><tr><th></th>");
				for (String column : columnName) {		
						sb.append("<th>" + column + "</th>");									
				}
				sb.append("</tr>");

				for (int i : index) {
					sb.append("<tr>");
					sb.append("<td>" + requestTable.get(keys.get(i)).key() + "</td>");
					for (String column : columnName) {
						if (requestTable.get(keys.get(i)).get(column) != null) {
							sb.append("<td>" + requestTable.get(keys.get(i)).get(column) + "</td>");
						} else {
							sb.append("<td></td>");
						}
					}
					sb.append("</tr>");
				}
				sb.append("<table>");
				if (end != true) {
					sb.append("<div style=\"position: relative; width: 600px; height: 800px;\">");
				    sb.append("<div style=\"position: absolute; bottom: 5px;\">");
				    String hyperlink = "\"" + "/view/" + table + "?fromRow=" + requestTable.get(keys.get(index.get(index.size()-1)+1)).key() + "\"";
					sb.append("<td><a href=" + hyperlink + ">" + "Next" + "</a></td>");
				    sb.append("</div></div>");
				}
				sb.append("</body></html>\r\n");
				String content = sb.toString();
				System.out.println(content);
				res.body(content);
				return null;
			}
		});

		put("/persist/:table", (req, res) -> {

			synchronized (data) {
				System.out.println("persistent");
				String table = req.params("table");

				if (data.get(table) == null) {
					res.status(200, "OK");
					res.body("OK");
					data.put(table, new ConcurrentHashMap<String, Row>());
					File file = new File(directory, table + ".table");
					file.createNewFile();
					pTable.add(table);
				} else {
					res.status(403, "Forbidden");
					res.body("Forbidden");
				}
				return null;
			}
		});
		
		get("/data/:table/:row", (req, res) -> {
			synchronized (data) {
				String table = req.params("table");
				String row = req.params("row");
				Row theRow = getRow(table, row);

				if (theRow == null) {
					res.status(404, "Not Found");
					res.body("Not Found");
				} else {
					res.status(200, "Ok");
					res.bodyAsBytes(theRow.toByteArray());
				}
				return null;
			}
		});
		
		get("/data/:table", (req, res) -> {
			synchronized (data) {
				String table = req.params("table");
				String start = req.queryParams("startRow");
				String end = req.queryParams("endRowExclusive");
				if (data.get(table) == null) {
					res.status(404, "Not Found");
					res.body("Not Found");
				} else {
					res.status(200, "OK");
					res.type("text/plain");
					byte[] f = { 10 };
					for (Map.Entry<String, Row> entry : data.get(table).entrySet()) {
						if (start == null || entry.getKey().compareTo(start) >= 0) {
							if (end == null || entry.getKey().compareTo(end) < 0) {
								res.write(getRow(table,entry.getKey()).toByteArray());
								res.write(f);

							}
						}
					}
					res.write(f);
				}
				return null;
			}
		});

		get("/count/:table", (req, res) -> {
			synchronized (data) {
				String table = req.params("table");
				if (data.get(table) == null) {
					res.status(404, "Not Found");
					res.body("Not Found");
				} else {
					res.status(200, "OK");
					res.type("text/plain");
					res.body(String.valueOf(data.get(table).size()));
				}
				return null;
			}
		});

		put("/data/:table", (req, res) -> {
			synchronized (data) {
				String table = req.params("table");
				res.status(200, "OK");
				res.body("OK");
				InputStream in = new ByteArrayInputStream(req.bodyAsBytes());
				while (true) {					
					Row row = Row.readFrom(in);
					if (row == null) {
						break;
					}
					putRow(table, row);
				}
				return null;
			}
		});
		
		put("/delete/:table", (req, res) -> {
			synchronized (data) {
				String table = req.params("table");
				if (data.get(table) == null) {
					res.status(404, "Not Found");
					res.body("Not Found");
					return null;
				}
				res.status(200, "OK");
				res.body("OK");
				File file = new File(directory, table + ".table");
				file.delete();
				data.remove(table);
				pTable.remove(table);
				return null;
			}
		});
		
		get("/tables", (req, res) -> {
			synchronized (data) {
				res.status(200, "OK");
				res.type("text/plain");
				StringBuilder sb = new StringBuilder();
				for (String table : data.keySet()) {
					sb.append(table);
					sb.append("\n");
				}
				res.body(sb.toString());
				return null;
			}
		});
		
		
		put("/rename/:table", (req, res) -> {
			synchronized (data) {
				String tableOld = req.params("table");
				String tableNew = req.body();
				if (data.get(tableOld) == null) {
					res.status(404, "Not Found");
					res.body("Not Found");
					return null;
				} else if (data.get(tableNew) != null) {
					res.status(409, "Conflict");
					res.body("Conflict");
					return null;
				}
				res.status(200, "OK");
				res.body("OK");
				ConcurrentHashMap<String, Row> requestTable = data.get(tableOld);
				data.remove(tableOld);
				data.put(tableNew, requestTable);
				if (pTable.contains(tableOld)) {
					pTable.remove(tableOld);
					pTable.add(tableNew);
				}
				File fileOld = new File(directory, tableOld+".table");
				File fileNew = new File(directory, tableNew+".table");
				fileOld.renameTo(fileNew);
				return null;
			}
		});
	}
}
