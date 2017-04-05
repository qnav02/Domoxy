package agenteInterno;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import pm.ProxyMessage;

/**
 * This class opens a new pending connection with the external proxy {@link Server} according to the configuration.
 * For each incoming request a new {@link ReceiverThread} is created.
 * 
 * @see {@link Server}
 * @see {@link ReceiverThread}
 *
 */
public class Receiver implements Runnable {

	private boolean secure;
	private String address;
	private String port;
	private String proxyAddress;
	private int proxyPort;
	private String uri;

	private int cnt;
	
	private BufferedReader br;
	private InputStreamReader is;
	private boolean down;

	/**
	 * Constructor.
	 * 
	 * @param address
	 * @param port
	 * @param proxyAddress
	 * @param proxyPort
	 * @param uri
	 */
	public Receiver(String address, String port, String proxyAddress, int proxyPort, String uri) {
		this.address = address;
		this.port = port;
		this.proxyAddress = proxyAddress;
		this.proxyPort = proxyPort;
		this.uri = uri;
		this.cnt = 0;
		this.br = null;
		this.is = null;
		this.down = false;
	}

	@Override
	public void run() {
		ProxyMessage pm = null;

		/*------------------------------ URL configuration ------------------------------*/
		try {
			if (uri.startsWith("https")) {
				secure = true;

				is = sslConfiguration();
				new Thread(){
					public void run(){
						while(true){
						try {
							if(!down){
								this.currentThread().sleep(120000);
								SSLSocketFactory ssf = (SSLSocketFactory) SSLSocketFactory.getDefault();
								SSLSocket ssc = (SSLSocket) ssf.createSocket(proxyAddress, proxyPort);
								ssc.startHandshake();
								URL url = new URL(uri+"___IAA___");
								HttpsURLConnection sconn = (HttpsURLConnection) url.openConnection();
								sconn.setSSLSocketFactory(ssf);
								sconn.setReadTimeout(0);
								sconn.setRequestMethod("POST");
		
								if (sconn.getResponseCode() != 200) {
									throw new RuntimeException("Failed : HTTPS error code : " + sconn.getResponseCode());
								}
								sconn.disconnect();
								}
							else{
								br = reConfig(is);
							}
							} catch(Exception e){
								down = true;
							}
						}
					}
				}.start();
			} else {
				secure = false;

				is = configuration();
				new Thread() {
					public void run(){
						while(true){
						try{
							if(!down){
								this.currentThread().sleep(120000);
								URL url = new URL(uri+"___IAA___");
								HttpURLConnection conn = (HttpURLConnection) url.openConnection();
								conn.setUseCaches(true);
								conn.setReadTimeout(0);
								conn.setRequestMethod("POST");
		
								if (conn.getResponseCode() != 200) {
									throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
								}
								conn.disconnect();
								}
							else{
								br = reConfig(is);
							}
							} catch(Exception e){
								down = true;
							}
						}
					}
				}.start();
			}

			br = new BufferedReader(is);
		} catch (Exception e) {
			br = reConfig(is);
		}
		/*------------------------------ configuration end ------------------------------*/

		while (true) {
			try {
				if(!down){
					String l = br.readLine();
					if(l != null){
						if (!l.contains("IAA") && l.contains("@#@")) {
							System.out.println("READLINE: " + l);
							pm = new ProxyMessage(l.split("@#@")[0], l.split("@#@")[1], l.split("@#@")[2]);
							if (pm != null) {
								System.out.println("Object received = " + pm.getId_thread() + " uri: " + pm.getUri() + " method: " + pm.getMethod());
								// device connection and main proxy server contacting
								new Thread(new ReceiverThread(secure, address, port, proxyAddress, proxyPort, pm)).start();
								cnt = 0;
							}
						}
					}
				}
				else{
					br = reConfig(is);
				}
			} catch (Exception e) {
				down = true;
			}
		}
	}

	/**
	 * It starts a new secure configuration with the server.
	 * 
	 * @return an {@link InputStreamReader} used to read the incoming requests from the main server.
	 * 
	 * @throws UnknownHostException if a wrong proxy server address is provided.
	 * @throws IOException if the connection fails.
	 * 
	 * @see {@link SSLSocket}
	 * @see {@link HttpsURLConnection}
	 */
	private InputStreamReader sslConfiguration() throws UnknownHostException, IOException {
		System.setProperty("javax.net.ssl.trustStore", Discovery.getTruststoreLocation());
		System.setProperty("javax.net.ssl.keyStore", Discovery.getKeystoreLocation());
		System.setProperty("javax.net.ssl.keyStorePassword", Discovery.getKeystorePassword());

		SSLSocketFactory ssf = (SSLSocketFactory) SSLSocketFactory.getDefault();
		SSLSocket ssc = (SSLSocket) ssf.createSocket(proxyAddress, proxyPort);
		ssc.startHandshake();
		URL url = new URL(uri);
		HttpsURLConnection sconn = (HttpsURLConnection) url.openConnection();
		sconn.setSSLSocketFactory(ssf);
		sconn.setReadTimeout(0);
		sconn.setRequestMethod("POST");

		if (sconn.getResponseCode() != 200) {
			throw new RuntimeException("Failed : HTTPS error code : " + sconn.getResponseCode());
		}
		System.out.println("opened secure connection: " + uri);
		return new InputStreamReader(sconn.getInputStream());
	}

	/**
	 * It starts a new plaintext configuration with the server.
	 * 
	 * @return an {@link InputStreamReader} used to read the incoming requests from the main server.
	 * 
	 * @throws IOException if the connection fails.
	 * 
	 * @see {@link HttpURLConnection}
	 */
	private InputStreamReader configuration() throws IOException {
		URL url = new URL(uri);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setReadTimeout(0);
		conn.setRequestMethod("POST");

		if (conn.getResponseCode() != 200) {
			throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
		}
		System.out.println("opened connection: " + uri);
		return new InputStreamReader(conn.getInputStream());
	}
	
	/**
	 * It tries again the configuration process if something is not working.
	 * 
	 * @param is the current {@link InputStreamReader}
	 * @return a new {@link BufferedReader}
	 */
	private BufferedReader reConfig(InputStreamReader is) {
		BufferedReader br = null;
		try{
			if(++cnt < 3){
				System.out.println("Attempting re-configuration...");
				if(secure){
					is = sslConfiguration();
				} 
				else{
					is = configuration();
				} 
				br = new BufferedReader(is);
				} 
			else{
				Thread.currentThread();
				Thread.sleep(60000); // server down!
				cnt = 0;
			}
			down = false;
		}catch(Exception e1){
			down = true;
		}
		return br;
	}
}
