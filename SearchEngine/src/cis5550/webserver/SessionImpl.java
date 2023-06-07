package cis5550.webserver;

import java.util.HashMap;
import java.util.Map;

public class SessionImpl implements Session {
	
	String id;
	long creationTime;
	long lastAccessedTime;
	int maxActiveInterval = 300;
	boolean valid = true;
	Map<String,Object> attributes = new HashMap<String,Object>();
	
	SessionImpl(String id){
		this.id = id;
	}
	
	
	@Override
	public String id() {
		return this.id;
	}

	@Override
	public long creationTime() {
		return this.creationTime;
	}

	@Override
	public long lastAccessedTime() {
		return this.lastAccessedTime;
	}

	@Override
	public void maxActiveInterval(int seconds) {
		this.maxActiveInterval = seconds;

	}

	@Override
	public void invalidate() {
		this.valid = false;
	}

	@Override
	public Object attribute(String name) {
		return attributes.get(name);
	}

	@Override
	public void attribute(String name, Object value) {
		attributes.put(name, value);

	}

}
