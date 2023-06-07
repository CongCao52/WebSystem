package cis5550.frontend;

import static cis5550.webserver.Server.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.*;


 class RateLimiter {

    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();
    private final int rate;
    private final int burst;
    private volatile long counterStamp = System.currentTimeMillis();

    public RateLimiter(int rate, int burst) {
        this.rate = rate;
        this.burst = burst;
    }

    public boolean allow(String clientId) {
        long now = System.currentTimeMillis();
        AtomicInteger counter = counters.computeIfAbsent(clientId, k -> new AtomicInteger());
        synchronized (counter) {
            int tokens = counter.get();
            int maxTokens = Math.min(tokens + (int) ((now - counterStamp) * rate / 1000), burst);
            if (maxTokens < 1) {
                return false;
            }
            counter.set(maxTokens - 1);
            counterStamp = now;
        }
        return true;
    }
}

public class Frontend {
	public static void main(String args[]) throws Exception {

		if (args.length != 3) {
			System.err.println("Frontend ip port and Ranking ip:port");
			return;
		}
		
		String insertionPoint = "INSERT_IP_PORT_HERE";
		
		int port;				
				
		try {
			port = Integer.valueOf(args[1]);
		} catch (Exception e) {
			System.err.println("Frontend port is not an integer");
			return;
		}
		
		String frontendIpandPort = args[0] + ":" + port;
		String rankingIpandPort = args[2];
		
		securePort(443);
		
		RateLimiter rl = new RateLimiter(20,20);

		get("/", (req, res) -> {
			boolean pass = rl.allow(req.ip());
			if (pass == false) {
				res.type("text/html");
				res.body("<html><body><h1>You are not welcome!</h1></body></html>\r\n");
				return null;
			}
			res.type("text/html");
			String st = Files.readString(Paths.get("src/cis5550/frontend/Main.html"));
			st=st.replace(insertionPoint, frontendIpandPort);
			res.body(st);
			return null;
		});

		// Communicate ranking to get information
		get("/search/:term", (req, res) -> {
			boolean pass = rl.allow(req.ip());
			if (pass == false) {
				res.body("BAD");
				return null;
			}
		    String url = "http://" + rankingIpandPort + "/search/" + req.params("term");
		    URL obj = new URL(url);
		    HttpURLConnection con = (HttpURLConnection) obj.openConnection();
		    con.setRequestMethod("GET");
		    int responseCode = con.getResponseCode();
		    if (responseCode == 200) {
		        BufferedInputStream in = new BufferedInputStream(con.getInputStream());
		        
		        // Read the input stream in chunks
		        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		        byte[] data = new byte[1024]; // Adjust the buffer size as needed
		        int bytesRead;
		        while ((bytesRead = in.read(data, 0, data.length)) != -1) {
		            buffer.write(data, 0, bytesRead);
		        }
		        String content = new String(buffer.toByteArray());
		        return content;
		    } else {
		        return null;
		    }
		});
		
		get("/findwords/:term", (req, res) -> {
			boolean pass = rl.allow(req.ip());
			if (pass == false) {
				res.body("BAD");
				return null;
			}
			String url = "http://" + rankingIpandPort + "/findwords/" + req.params("term");
			URL obj = new URL(url);
			HttpURLConnection con = (HttpURLConnection) obj.openConnection();
			con.setRequestMethod("GET");
			int responseCode = con.getResponseCode();
			if (responseCode == 200) {
				BufferedInputStream in = new BufferedInputStream(con.getInputStream());
				String content = new String(in.readAllBytes());
				return content;
			} else {
				return null;
			}
		});
	}
}
