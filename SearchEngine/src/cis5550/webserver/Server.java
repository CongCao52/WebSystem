package cis5550.webserver;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.concurrent.*;

import javax.net.ServerSocketFactory;
import javax.net.ssl.*;
import java.security.*;

import cis5550.tools.Logger;


public class Server extends Thread{
	
	static Server server = null;
	static boolean flag = false;
	static HashMap<String, hostInfo> hosts = new HashMap<String, hostInfo>();
	static String currentHost = "defaultHost";
	static HashMap<String, Route> routeMapGET = new HashMap<String, Route>();
	static HashMap<String, Route> routeMapPUT = new HashMap<String, Route>();
	static HashMap<String, Route> routeMapPOST = new HashMap<String, Route>();
	static Filter before = null;
	static Filter after = null;
	final static SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
	static final int NUM_WORKERS = 100;
	static File directory = null;
	int port = -1;
	int HTTPSport = -1;
	static final String SERVER_NAME = "MyServer";
	static ConcurrentHashMap<String, SessionImpl> sessions = new ConcurrentHashMap<String, SessionImpl>();
	
	private static final Logger logger = Logger.getLogger(Server.class);
	
	public static class staticFiles {
		public static void location(String s) throws Exception {
			String directoryName = s;
			
			File directory2 = null;
			
			//if given directory is null, return;
			
			directory2 = new File(directoryName);
			
			//if given directory is not a directory, return;
			if (!directory2.isDirectory()) {
				throw new Exception("Not a directory.");
			}
			
			if (server == null) {
				server = new Server(directory2,80);
			} else {
				directory = directory2;
			}
			if (flag == false) {
				flag = true;
				server.start();
			}
		}
	}
	
	static class hostInfo {
		HashMap<String, Route> routeMapGET;
		HashMap<String, Route> routeMapPUT;
		HashMap<String, Route> routeMapPOST;
		File directory;
		
		hostInfo(HashMap<String, Route> routeMapGET, HashMap<String, Route> routeMapPUT, HashMap<String, Route> routeMapPOST, File directory) {
			this.routeMapGET = routeMapGET;
			this.routeMapPUT = routeMapPUT;
			this.routeMapPOST = routeMapPOST;
			this.directory = directory;
		}
		
		static void switchHost (String hostName) {
			if (Server.currentHost.equals(hostName)) {
				return;
			} else {
				if (Server.hosts.get(Server.currentHost) == null) {
					Server.hosts.put(Server.currentHost, new hostInfo(Server.routeMapGET, Server.routeMapPUT,Server.routeMapPOST,Server.directory));
				} else {
					hostInfo toSave = Server.hosts.get(Server.currentHost);
					toSave.routeMapGET = Server.routeMapGET;
					toSave.routeMapPUT = Server.routeMapPUT;
					toSave.routeMapPOST = Server.routeMapPOST;
					toSave.directory = Server.directory;
				}
				if (Server.hosts.get(hostName) == null) {
					Server.hosts.put(hostName, new hostInfo(new HashMap<String, Route>(), new HashMap<String, Route>(),
							new HashMap<String, Route>(),null));
				}
				hostInfo toUse = Server.hosts.get(hostName);
				Server.routeMapGET = toUse.routeMapGET;
				Server.routeMapPUT = toUse.routeMapPUT;
				Server.routeMapPOST = toUse.routeMapPOST;
				Server.directory = toUse.directory;
				Server.currentHost = hostName;
			}
		}
	}
	
	public static void get(String p, Route route) {
		if (server == null) {
			server = new Server(null,80);
		}
		if (flag == false) {
			flag = true;
			server.start();
		}
		routeMapGET.put(p, route);
	}
	
	public static void post(String p, Route route) {
		if (server == null) {
			server = new Server(null,80);
		}
		if (flag == false) {
			flag = true;
			server.start();
		}
		routeMapPOST.put(p, route);
	}
	
	public static void put(String p, Route route) {
		if (server == null) {
			server = new Server(null,80);
		}
		if (flag == false) {
			flag = true;
			server.start();
		}
		routeMapPUT.put(p, route);
	}
	
	public static void port(int portNumber) {
		if (server == null) {
			server = new Server(null,portNumber);
		}
	}
	
	public static void before(Filter filter) {
		before = filter;
	}
	
	public static void after(Filter filter) {
		after = filter;
	}
	
	public static void host (String newHost) {
		hostInfo.switchHost(newHost);
	}
	
	public static void securePort(int portNumber) {
		if (server == null) {
			server = new Server(null, 80);
		}
		server.HTTPSport = portNumber;
	}
	/**
	 * Constructor 
	 * @param directory for the server to find files
	 * @param port port number
	 */
	public Server (File directory, int port) {
		Server.directory = directory;
		this.port = port;
	}
	
	
	private class BeginAccepting implements Runnable{
		
		ServerSocket serverSocket;
		CustomThreadPoolExecutor pool;
		
		BeginAccepting(ServerSocket s, CustomThreadPoolExecutor pool) {
			this.serverSocket = s;
			this.pool = pool;
		}
		
		@Override
		public void run() {
			while (true) {
				Socket socket;
				try {
					socket = serverSocket.accept();
					logger.info("New socket created." + socket.getRemoteSocketAddress());
					Runnable task = new RequestHandler(socket, SERVER_NAME);
					pool.submit(task);
				} catch (IOException e) {
					logger.warn("Can not initiate socket.", e);
					System.out.println("Written by Haoming Wu");
				}
				
			}
			
		}
	}
	
	

	@Override
	public void run() {
		CleanSession clean = new CleanSession(Server.sessions);
		clean.start();
		BlockingQueue<Runnable> blockingQueue = new LinkedBlockingQueue<Runnable>();
		CustomThreadPoolExecutor pool = new CustomThreadPoolExecutor(NUM_WORKERS, NUM_WORKERS, 0, TimeUnit.SECONDS, blockingQueue);
		try {
			//System.out.println("start.");
			//System.out.println("Port number: " + port);
			//System.out.println("Port number: " + this.HTTPSport);
			ServerSocket serverSocket = new ServerSocket(port);
			logger.info("serverSocket created successfully.");
			logger.info("Port number: " + port);
			//System.out.println("Port number: " + port);
			Runnable task = new BeginAccepting(serverSocket, pool);
			pool.submit(task);
			if (this.HTTPSport != -1) {
				//System.out.println("Start creating HTTPS");
				String pwd = "secret";
				KeyStore keyStore = KeyStore.getInstance("JKS");
				keyStore.load(new FileInputStream("keystore.jks"), pwd.toCharArray());
				KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
				keyManagerFactory.init(keyStore, pwd.toCharArray());
				SSLContext sslContext = SSLContext.getInstance("TLS");
				sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
				ServerSocketFactory factory = sslContext.getServerSocketFactory();
				ServerSocket serverSocketTLS = factory.createServerSocket(this.HTTPSport);
				//System.out.println("Port number: " + this.HTTPSport);
				logger.info("serverSocketTLS created successfully.");
				logger.info("Port number: " + HTTPSport);
				Runnable task2 = new BeginAccepting(serverSocketTLS, pool);
				pool.submit(task2);
			}
		} catch (Exception e) {
			logger.warn("Can not initiate socket.", e);
			//System.out.println(e);
			System.out.println("Written by Haoming Wu");
		}
	}
	

}
