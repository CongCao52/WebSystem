package cis5550.webserver;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

import cis5550.tools.Logger;
import cis5550.webserver.Server.hostInfo;

public class RequestHandler implements Runnable {

	private Socket socket;
	private String fileName;
	private static final Logger logger = Logger.getLogger(RequestHandler.class);
	private String serverName;

	public RequestHandler(Socket socket, String serverName) {
		this.socket = socket;
		this.serverName = serverName;
	}

	private void errorHandler(String error, PrintWriter w) throws IOException {
		String status = null;

		if (error.equals("400")) {
			status = "400 Bad Request";
		}
		if (error.equals("403")) {
			status = "403 Forbidden";
		}
		if (error.equals("404")) {
			status = "404 Not Found";
		}
		if (error.equals("405")) {
			status = "405 Not Allowed";
		}
		if (error.equals("501")) {
			status = "501 Not Implemented";
		}
		if (error.equals("505")) {
			status = "505 HTTP Version Not Supported";
		}
		if (error.equals("416")) {
			status = "416 Range Not Satisfiable";
		}
		if (error.equals("500")) {
			status = "500 Internal Server Error";
		}
		sendHeaders(w, status, "text/plain", status.getBytes().length);
		w.print(status);
		w.flush();
	}

	private void sendHeaders(PrintWriter w, String status, String contentType, int length) throws IOException {
		w.print("HTTP/1.1 " + status + "\r\n");
		w.print("Content-Type: " + contentType + "\r\n");
		w.print("Server: " + serverName + "\r\n");
		w.print("Content-Length: " + length + "\r\n\r\n");
		w.flush();
	}

	private void sendHeaders(PrintWriter w, String status, String contentType, int length, int begin, int end,
			int totalLength) throws IOException {
		w.print("HTTP/1.1 " + status + "\r\n");
		w.print("Content-Type: " + contentType + "\r\n");
		w.print("Server: " + serverName + "\r\n");
		w.print("Content-Range: bytes " + begin + "-" + end + "/" + totalLength + "\r\n");
		w.print("Content-Length: " + length + "\r\n\r\n");
		w.flush();
	}
	
	private void dynamicHandler(String method, Route route, PrintWriter w, OutputStream rawOut, String path, String protocol, Map<String, String> headers, byte[] data, RequestImpl req, ResponseImpl res) throws IOException {
			try {
				Object result = route.handle(req, res);
				if (res.writeCalled != 0) {
					rawOut.close();
					socket.close();
				} else {
					byte[] content;
					if (result != null) {
						content = result.toString().getBytes();
						if (res.headers.get("content-type") == null) {
							res.headers.put("content-type" , "txt/plain");
						}
						res.headers.put("content-length", String.valueOf(content.length));
					} else if (res.body != null && res.body.length >0) {
						content = res.body;
						res.headers.put("content-length", String.valueOf(content.length));
						
					} else {
						content = null;
						res.headers.put("content-length", "0");
					}
					
					w.print(req.protocol() + " " + res.statusCode + " " + res.reasonPhrase + "\r\n");
					if (res.headers.get("server") == null) {
						res.headers.put("server" , serverName);
					}
					if (res.req.setCookie == true) {
						if (req.url().toLowerCase().contains("https")) {
							res.headers.put("Set-Cookie", "SessionID="+res.req.session().id() + "; HttpOnly; Secure; SameSite=Strict");
						} else {
							res.headers.put("Set-Cookie", "SessionID="+res.req.session().id() + "; HttpOnly; SameSite=Strict");
						}
					}
					StringBuilder header = new StringBuilder();
					for (Map.Entry<String, String> entry : res.headers.entrySet()) {
						header.append(entry.getKey());
						header.append(": ");
						header.append(entry.getValue());
						header.append("\r\n");
					}
					header.append("\r\n");
					String contentString = header.toString();
					w.write(contentString);
					w.flush();
					if (content != null) {
						rawOut.write(content);
						rawOut.flush();
					}

				}
				return;
			} catch (Exception e) {			
				if (res.writeCalled !=0) {
					socket.close();
				} else {
					errorHandler("500",w);
				}
			}
		
	}
	private Route pathMatching(String path, Map<String, Route> routeMap, RequestImpl req) throws UnsupportedEncodingException {
		Route route = null;
		//System.out.println("Matching begins");
		Map <String, String> param = new HashMap<String, String>();
		if (path.contains("?")) {
			path = path.split("\\?")[0];
		}
		//System.out.println(path);
		for (Map.Entry<String, Route> entry : routeMap.entrySet()) {
			String[] pathTokens = path.split("/");
			String[] entryTokens = entry.getKey().split("/");
			if (pathTokens.length != entryTokens.length) {
				//System.out.println("Different length: " + pathTokens);
				continue;
			}
			boolean match = true;
			
			for (int i=0; i< pathTokens.length; i++) {
				if (!entryTokens[i].equals("*") && !entryTokens[i].startsWith(":") && !pathTokens[i].equals(entryTokens[i])) {
					//System.out.println("Different char: " + pathTokens[i]);
					match = false;
					param.clear();
					break;
				}
				if (entryTokens[i].startsWith(":")) {
					//System.out.println("Added" + entryTokens[i].substring(1) + pathTokens[i]);
					param.put(entryTokens[i].substring(1), pathTokens[i]);
				}
			}
			if (match == true) {
				//System.out.println("Matched!");
				//System.out.println("key: " + entry.getKey());
				route = routeMap.get(entry.getKey());
				//System.out.println(route);
				req.setParams(param);
				req.queryParams = getQuery(req);
				//System.out.println("finished");
				break;
			}
		}
		return route;		
	}


	private Map<String, String> getQuery (RequestImpl req) throws UnsupportedEncodingException {
		String path = req.url();
		//System.out.println(path);
		Map<String, String> queryMap = new HashMap<String, String>();
		if (path.contains("?")) {
			String content = path.split("\\?")[1];
			for (String pair : content.split("&")) {
				queryMap.put(URLDecoder.decode(pair.split("=")[0],"UTF-8"), URLDecoder.decode(pair.split("=")[1],"UTF-8"));
			}
		}
		String type = req.contentType();
		if (type != null && type.equals("application/x-www-form-urlencoded")) {
				String content = req.body();
				for (String pair : content.split("&")) {
					queryMap.put(URLDecoder.decode(pair.split("=")[0],"UTF-8"), URLDecoder.decode(pair.split("=")[1],"UTF-8"));
				}
			
		}
		return queryMap;
	}
	
	@Override
	public void run() {
		//System.out.print("run");

		try {
			OutputStream rawOut = new BufferedOutputStream(socket.getOutputStream());
			PrintWriter w = new PrintWriter(socket.getOutputStream());
			InputStream rawIn = new BufferedInputStream(socket.getInputStream());
			ByteArrayOutputStream bytesReceived = new ByteArrayOutputStream();
			//int timeoutInMillis = 10000;
			//socket.setSoTimeout(timeoutInMillis);

			int num = 0; // number of matched character CR LF

			while (true) {
				int b = rawIn.read();
				if (b == -1) {
					socket.close();
					break;
				}
				if ((((num == 0) || (num == 2)) && (b == '\r')) || (((num == 1) || (num == 3)) && (b == '\n'))) {
					num++;
				} else {
					num = 0;
				}
				bytesReceived.write(b);
				//System.out.print(b+" ");

				if (num == 4) {
					num = 0;
					byte[] bytes = bytesReceived.toByteArray();
					BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes)));
					String requestLine = reader.readLine();
					//System.out.print(requestLine);
					Integer contentLength = null;
					String host = null;
					String HTTPTime = null;
					String range = null;
					SessionImpl session = null;
					Map<String, String> headers = new HashMap<String,String>();
					String header = reader.readLine();
					while (header != null) {
						if (!header.contains(":")) {
							break;
						}
						String thisHeader = header.toLowerCase().split(": ")[0];
						headers.put(thisHeader, header.split(":")[1].trim());
						System.out.println(thisHeader + " " + header.split(":")[1].trim());
						
						if (thisHeader.equals("content-length")) {
							contentLength = Integer.valueOf(header.split(":")[1].trim());
						}
						if (thisHeader.equals("host")) {
							host = header.split(":")[1].trim().toLowerCase();
						}
						if (thisHeader.equals("if-modified-since")) {
							HTTPTime = header.split(": ")[1];
						}
						if (thisHeader.equals("range")) {
							range = header.split(": ")[1];
						}
						if (thisHeader.equals("cookie") || header.split(": ")[1].toLowerCase().contains("sessionid")) {
							String sessionID = header.split(": ")[1].split("=")[1];
							System.out.println("sessionID=" + sessionID);
							session = Server.sessions.get(sessionID);
							if (session != null && session.valid == true && session.lastAccessedTime + session.maxActiveInterval*1000 >= System.currentTimeMillis()) {
								session.lastAccessedTime = System.currentTimeMillis();
								System.out.println("updated lastAccessedTime.");
							} else {
								session = null;
							}
						}
						header = reader.readLine();
						System.out.println("read again");
						System.out.println(header);
					}
					//System.out.println("readline ends");
					if (host == null) {
						//System.out.println("400");
						errorHandler("400", w);
						continue;
					} else {
						if (Server.hosts.get(host)  == null) {
							hostInfo.switchHost("defaultHost");
						} else {
							hostInfo.switchHost(host);
						}
					}
					if (contentLength != null && contentLength <= 0) {
						//System.out.println("400");
						errorHandler("400", w);
						continue;
					}
					byte[] data = null;
					if (contentLength != null && contentLength > 0) {
						data = rawIn.readNBytes(contentLength);
						//System.out.println("data");
					}
					RequestLineHandler(requestLine, w, rawOut, HTTPTime, range, headers, data, session);
					bytesReceived.reset();
					if (socket.isClosed()) {
						break;
					}
				}
			}
		} catch (IOException | ParseException e1) {
			logger.error("Cannot communicate with client socket.");
			e1.printStackTrace();
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void RequestLineHandler(String requestLine, PrintWriter w, OutputStream rawOut, String HTTPTime,
			String range, Map<String, String> headers, byte[] data, SessionImpl session) throws IOException, ParseException {
		//System.out.println("RequestLineHandler Started.");
		String[] tokens = requestLine.split("\\s+");
		if (tokens.length != 3) {
			errorHandler("400", w);
			return;
		}
		String method = tokens[0].trim().toUpperCase();
		fileName = tokens[1].trim();
		String path = tokens[1].trim();
		if (fileName.startsWith("/")) {
			fileName = fileName.substring(1);
		}
		String protocol = tokens[2].trim();
		InetSocketAddress add = new InetSocketAddress((((InetSocketAddress) socket.getRemoteSocketAddress()).getAddress()).toString().replace("/",""), socket.getPort());
		RequestImpl req = new RequestImpl(method, path, protocol, headers, null, null, add, data, Server.server, session);
		ResponseImpl res = new ResponseImpl(req, socket);

		if (method.equals("GET")) {
			System.out.println("GET");
			System.out.println("Path: "+ path);
			Route route = pathMatching(path, Server.routeMapGET,req);
			if (route != null) {
				dynamicHandler("GET",route, w, rawOut, path, protocol, headers, data, req, res);
				return;
			}
		} else if (method.equals("PUT")) {
			//System.out.println("PUT");
			//System.out.println("Path: "+ path);
			Route route = pathMatching(path, Server.routeMapPUT,req);
			if (route != null) {
				dynamicHandler("PUT",route, w, rawOut, path, protocol, headers, data,req, res);
				return;
			}

		} else if (method.equals("POST")) {
			//System.out.println("POST");
			//System.out.println("Path: "+ path);
			Route route = pathMatching(path, Server.routeMapPOST,req);
			if (route != null) {
				dynamicHandler("POST",route, w, rawOut, path, protocol, headers, data, req, res);
				return;
			}
		}

		if (method.equals("POST") || method.equals("PUT")) {
			errorHandler("405", w);
			return;
		}

		if ((!method.equals("GET")) && (!method.equals("HEAD")) && (!method.equals("POST"))
				&& (!method.equals("PUT"))) {
			errorHandler("501", w);
			return;
		}

		if (!protocol.equals("HTTP/1.1")) {
			errorHandler("505", w);
			return;
		}

		if (fileName.contains("..")) {
			errorHandler("403", w);
			return;
		}

		File returnFile = new File(Server.directory, fileName);

		if (Server.directory == null) {
			return;
		}

		if (!returnFile.exists()) {
			errorHandler("404", w);
			return;
		}

		if (!returnFile.canRead()) {
			errorHandler("403", w);
			return;
		}

		if (HTTPTime != null && returnFile.lastModified() / 1000 * 1000 <= Server.format.parse(HTTPTime).getTime()) {
			sendHeaders(w, "304 Not Modified", "text/plain", 0);
			return;
		}

		String contentType = "application/octet-stream";

		if (fileName.contains(".")) {
			String extension = fileName.split("\\.")[1].toLowerCase();

			if (extension.equals("jpg") || extension.equals("jpeg")) {
				contentType = "image/jpeg";
			} else if (extension.equals("txt")) {
				contentType = "text/plain";
			} else if (extension.equals("html")) {
				contentType = "text/html";
			}
		}

		byte[] theData = Files.readAllBytes(returnFile.toPath());
		int length = theData.length;

		int begin = 0;
		int end = length - 1;

		if (range != null) {

			if (range.split("-").length > 2) {
				errorHandler("416", w);
				return;
			}

			if (range.startsWith("-")) {
				try {
					end = Integer.valueOf(range.substring(1));
				} catch (Exception e) {
					errorHandler("416", w);
					return;
				}
			} else if (range.endsWith("-")) {
				try {
					begin = Integer.valueOf(range.substring(0, range.length() - 1));
				} catch (Exception e) {
					errorHandler("416", w);
					return;
				}
			} else {
				try {
					begin = Integer.valueOf(range.split("-")[0]);
					end = Integer.valueOf(range.split("-")[1]);
				} catch (Exception e) {
					errorHandler("416", w);
					return;
				}
			}

			if (begin < 0 || end >= length || begin > end) {
				errorHandler("416", w);
				return;
			}
		}

		if (begin != 0 || end != length - 1) {
			if (method.equals("HEAD") || method.equals("GET")) {
				sendHeaders(w, "206 Partial Content", contentType, end - begin + 1, begin, end, length);
			}

			if (method.equals("GET")) {
				rawOut.write(theData, begin, end - begin + 1);
				rawOut.flush();
			}
		}

		if (method.equals("HEAD") || method.equals("GET")) {
			sendHeaders(w, "200 OK", contentType, length);
		}

		if (method.equals("GET")) {
			rawOut.write(theData);
			rawOut.flush();
		}

	}
}
