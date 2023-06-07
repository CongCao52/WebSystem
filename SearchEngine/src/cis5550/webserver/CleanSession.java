package cis5550.webserver;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CleanSession extends Thread{
	ConcurrentHashMap<String, SessionImpl> sessions;
	
	CleanSession(ConcurrentHashMap<String, SessionImpl> sessions) {
		this.sessions = sessions;
	}
	
	@Override
	public void run(){
		while (true) {
			for (Map.Entry<String,SessionImpl> entrySet : this.sessions.entrySet()) {
				SessionImpl session = entrySet.getValue();
				if (session.valid == false) {
					sessions.remove(session.id());
				}
				if (session.lastAccessedTime + session.maxActiveInterval*1000 < System.currentTimeMillis()) {
					sessions.remove(session.id());
				}
			}
			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}	
		}
	}
}
