package cis5550.kvs;

import static cis5550.webserver.Server.*;

public class Master extends cis5550.generic.Master {
	public static void main(String args[]) {
		
		
		//parse the argument
		if (args.length != 1) {
			return;
		}
		int port = 0;
		try {
			port = Integer.valueOf(args[0]);
			
			//call Server.port to start the server
			port(port);
			
		} catch (Exception e) {
			return;
		}
		
		registerRoutes();
		
		//define GET route for / that returns a HTML with title and work table
		
		get("/", (req,res) -> { 
			res.type("text/html");
			StringBuilder sb = new StringBuilder();
			sb.append("<html><head><title>KVS Master</title></head><body><h1>KVS Master<h1><p>This is the worker table.</p>");
			sb.append(workerTable());
			sb.append("</body></html>\r\n");
			String html = sb.toString();
			res.body(html);System.out.println(html);
			return null;});
	}
}
