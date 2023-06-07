package cis5550.webserver;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ResponseImpl implements Response {
	
	int statusCode = 200;
	String reasonPhrase = "OK";
	boolean haltCalled = false;
	Map<String,String> headers = new HashMap<String,String>();
	byte[] body;
	int writeCalled = 0;
	RequestImpl req = null;
	Socket socket = null;
	OutputStream rawOut = null;
	
	ResponseImpl(RequestImpl req, Socket socket) throws IOException {
		this.req = req;
		this.socket = socket;
		rawOut = new BufferedOutputStream(socket.getOutputStream());
	}
	
	
	@Override
	public void body(String body) {
		bodyAsBytes(body.getBytes(StandardCharsets.UTF_8));

	}

	@Override
	public void bodyAsBytes(byte[] bodyArg) {
		body = bodyArg;
	}

	@Override
	public void header(String name, String value) {
		
		String existingValue = headers.get(name.toLowerCase());
		if (existingValue != null ) {
			headers.put(name.toLowerCase(), existingValue + "," + value);
		}
		else {
			headers.put(name.toLowerCase(), value);
		}

	}

	@Override
	public void type(String contentType) {
		header("CONTENT-TYPE", contentType);

	}

	@Override
	public void status(int statusCode, String reasonPhrase) {
		this.statusCode = statusCode;
		this.reasonPhrase = reasonPhrase;
	}

	@Override
	public void write(byte[] b) throws Exception {
		if (writeCalled == 0) {	
			StringBuilder content = new StringBuilder();
			content.append(req.protocol());
			content.append(" ");
			content.append(this.statusCode);
			content.append(" ");
			content.append(this.reasonPhrase);
			content.append("\r\n");
			for (Map.Entry<String, String> entry : this.headers.entrySet()) {
				if (entry.getKey().toLowerCase().equals("content-length")) {
					continue;
				}
				content.append(entry.getKey());
				content.append(": ");
				content.append(entry.getValue());
				content.append("\r\n");
			}
			if (this.req.setCookie = true) {
				content.append("Set-Cookie: SessionID="+ req.session().id() + "; HttpOnly; SameSite=Strict");
				if (this.req.url().toLowerCase().contains("https")) {
					content.append("; Secure\r\n");
				} else {
					content.append("\r\n");
				}
			}
			content.append("Connection: close\r\n\r\n");
			byte[] contentBytes = content.toString().getBytes();
			rawOut.write(contentBytes);
			rawOut.flush();
		}
		writeCalled ++;
		rawOut.write(b);
		rawOut.flush();
	}

	@Override
	public void redirect(String url, int responseCode) {
		header("location: ", url);
		if (responseCode == 301) {
			statusCode = 301;
			reasonPhrase = "Moved Permanently";
		} else if (responseCode == 302) {
			statusCode = 302;
			reasonPhrase = "Found";
		} else if (responseCode == 303) {
			statusCode = 303;
			reasonPhrase = "See Other";
		} else if (responseCode == 307) {
			statusCode = 307;
			reasonPhrase = "Temporary Redirect";
		} else if (responseCode == 308) {
			statusCode = 308;
			reasonPhrase = "Permanent Redirect";
		}

	}

	@Override
	public void halt(int statusCode, String reasonPhrase) {
		haltCalled = true;
		this.statusCode = statusCode;
		this.reasonPhrase = reasonPhrase;

	}

}
