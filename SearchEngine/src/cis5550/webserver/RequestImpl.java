package cis5550.webserver;

import java.util.*;
import java.net.*;
import java.nio.charset.*;

// Provided as part of the framework code

class RequestImpl implements Request {
  String method;
  String url;
  String protocol;
  InetSocketAddress remoteAddr;
  Map<String,String> headers;
  Map<String,String> queryParams;
  Map<String,String> params;
  byte bodyRaw[];
  Server server;
  SessionImpl session;
  boolean setCookie = false;

  RequestImpl(String methodArg, String urlArg, String protocolArg, Map<String,String> headersArg, Map<String,String> queryParamsArg, Map<String,String> paramsArg, InetSocketAddress remoteAddrArg, byte bodyRawArg[], Server serverArg, SessionImpl session) {
    method = methodArg;
    url = urlArg;
    remoteAddr = remoteAddrArg;
    protocol = protocolArg;
    headers = headersArg;
    queryParams = queryParamsArg;
    params = paramsArg;
    bodyRaw = bodyRawArg;
    server = serverArg;
    this.session = session;
  }

  public String requestMethod() {
  	return method;
  }
  public void setParams(Map<String,String> paramsArg) {
    params = paramsArg;
  }
  public int port() {
  	return remoteAddr.getPort();
  }
  public String url() {
  	return url;
  }
  public String protocol() {
  	return protocol;
  }
  public String contentType() {
  	return headers.get("content-type");
  }
  public String ip() {
  	return remoteAddr.getAddress().getHostAddress();
  }
  public String body() {
    return new String(bodyRaw, StandardCharsets.UTF_8);
  }
  public byte[] bodyAsBytes() {
  	return bodyRaw;
  }
  public int contentLength() {
  	return bodyRaw.length;
  }
  public String headers(String name) {
  	return headers.get(name.toLowerCase());
  }
  public Set<String> headers() {
  	return headers.keySet();
  }
  public String queryParams(String param) {
  	return queryParams.get(param);
  }
  public Set<String> queryParams() {
  	return queryParams.keySet();
  }
  public String params(String param) {
    return params.get(param);
  }
  public Map<String,String> params() {
    return params;
  }

@Override
public Session session() {
	System.out.println("session() is called.");
	if (this.session == null || this.session.valid == false || this.session.lastAccessedTime + this.session.maxActiveInterval*1000 < System.currentTimeMillis()) {
		String id = null;
		while(true) {
			String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890$#";
	        StringBuilder idBuilder = new StringBuilder();
	        Random rnd = new Random();
	        while (idBuilder.length() < 20) { 
	            int index = (int) (rnd.nextFloat() * chars.length());
	            idBuilder.append(chars.charAt(index));
	        }
	        id = idBuilder.toString();
	        if (Server.sessions.get(id) == null) {
	        	break;
	        }
		}
        SessionImpl newSession = new SessionImpl(id);
        newSession.creationTime = System.currentTimeMillis();
        newSession.lastAccessedTime = System.currentTimeMillis();
        newSession.attribute("HttpOnly", null);
        newSession.attribute("SameSite", "Strict");
        if (this.url().toLowerCase().contains("https")) {
        	newSession.attribute("Secure", null);
        }
        this.session = newSession;
        Server.sessions.put(id, newSession);
        this.setCookie = true;
	}
	return this.session;
}

}
