package cis5550.generic;

import java.net.HttpURLConnection;
import java.net.URL;

public class Worker {

	public static void startPingThread(String masterIDandPort, String workerID, int workerPort) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                URL url;
                try {
                    url = new URL("http://" + masterIDandPort + "/ping?id=" + workerID + "&port=" + workerPort);
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }

                while (true) {
                    try {
                        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                        connection.setRequestMethod("GET");
                        int responseCode = connection.getResponseCode();
                        connection.disconnect();

                        Thread.sleep(5000);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

        });
        t.start();
    }
}
