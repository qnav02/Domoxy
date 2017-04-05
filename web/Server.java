package web;

import java.io.FileReader;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.HashMap;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 * Multi-threaded Server class. A new {@link ServiceThread} is created for each incoming connection.
 * It handles the authorized accesses with an IP-based login system.
 * If the secure variable in the JSON configuration file is set to {@value true} the Server uses {@link SSLServerSocket}. Client authentication is required.
 *
 * @see {@link Lock}
 * @see javax.net.ServerSocketFactory
 */
public class Server {

	final private static String CONFIG = "./server_config.json";
	
	final private static String KEYSTORE_LOCATION = "./ServerKeyStore.jks";
	final private static String KEYSTORE_PASSWORD = "domserver";
	final private static String TRUSTSTORE_LOCATION = "./ServerTrustStore.jks";

	private static int port;
	private static boolean secure;
	private static String user;
	private static String pass;

	// login
	private static HashMap<String, Long> authorized;
	private static HashMap<String, String> prevUris;

	/**
	 * Constructor.
	 */
	public Server() {

	}

	/**
	 * Main.
	 * 
	 * @param args
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static void main(String[] args) throws InterruptedException {
		// read JSON file to configure the server
		JSONParser parser = new JSONParser();
		try {
			Object obj = parser.parse(new FileReader(CONFIG));
			JSONObject jsonObject = (JSONObject) obj;
			JSONObject proxy = (JSONObject) jsonObject.get("proxy");
			port = Integer.parseInt((String) proxy.get("port"));
			secure = Boolean.parseBoolean((String) proxy.get("secure"));
			user = (String) proxy.get("user");
			pass = (String) proxy.get("password");
		} catch (Exception e) {
			e.printStackTrace();
		}
		ConnectionTable ct = new ConnectionTable();
		HashMap<String, WaitTable> waitHash = new HashMap<String, WaitTable>();
		Lock lock = new Lock();
		authorized = new HashMap<String, Long>();
		prevUris = new HashMap<String, String>();
		if (!secure) {
			// unsecured Server
			try {
				ServerSocket welcomeSocket = new ServerSocket(port);
				System.out.println("Server started, port: " + port);
				while (true) {
					synchronized (lock) {
						while (lock.getLock() != true) {	// wait until accept is free
							try {
								lock.wait();
							} catch (InterruptedException ie) {
								System.out.println("ERROR: wait failed!");
							}
						}
						lock.changeLock(false);	// accept is locked
						Socket connSocket = welcomeSocket.accept();
						if (getLoginTime(getIp(connSocket.getRemoteSocketAddress())) != -1
								&& diffMillis(getLoginTime(getIp(connSocket.getRemoteSocketAddress()))) > 5 * 60 * 1000) {
							synchronized (authorized) {
								authorized.remove(getIp(connSocket.getRemoteSocketAddress()));
							}
						}
						updateLoginTime(getIp(connSocket.getRemoteSocketAddress()));
						new Thread(new ServiceThread(connSocket, waitHash, ct, lock)).start();
					}
				}
			} catch (IOException ioe) {
				System.out.println("ERROR: accept failed! " + ioe.getMessage());
				lock.changeLock(true);
			}
		}
		// SSL Server
		else{
			System.setProperty("javax.net.ssl.trustStore", TRUSTSTORE_LOCATION);
			System.setProperty("javax.net.ssl.keyStore", KEYSTORE_LOCATION);
			System.setProperty("javax.net.ssl.keyStorePassword", KEYSTORE_PASSWORD);
			try {
				ServerSocketFactory ssf = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
				SSLServerSocket serverSocket = (SSLServerSocket) ssf.createServerSocket(port);
				serverSocket.setNeedClientAuth(true);
				System.out.println("SSL Server started, port: " + port);
				while (true) {
					synchronized (lock) {
						while (lock.getLock() != true) {	// wait until accept is free
							try {
								lock.wait();
							} catch (InterruptedException ie) {
								System.out.println("ERROR: wait failed!");
							}
						}
						lock.changeLock(false);	// accept is locked
						Socket connSocket = serverSocket.accept();
						if (getLoginTime(getIp(connSocket.getRemoteSocketAddress())) != -1
								&& diffMillis(getLoginTime(getIp(connSocket.getRemoteSocketAddress()))) > 5 * 60 * 1000) {
							synchronized (authorized) {
								authorized.remove(getIp(connSocket.getRemoteSocketAddress()));
							}
						}
						updateLoginTime(getIp(connSocket.getRemoteSocketAddress()));
						new Thread(new ServiceThread(connSocket, waitHash, ct, lock)).start();
					}
				}
			} catch (IOException ioe) {
				System.out.println("ERROR: accept failed! " + ioe.getMessage());
				lock.changeLock(true);
			}
			
		}
	}

	/**
	 * It stores in a {@link HashMap} an IP address along with the current system time.
	 * @see java.util.HashMap#put(Object, Object)
	 *  
	 * @param socketAddress
	 */
	public synchronized static void setLogin(SocketAddress socketAddress) {
		authorized.put(getIp(socketAddress), System.currentTimeMillis());
	}

	/**
	 * @param time
	 * @return the difference in milliseconds between the current system time and the parameter.
	 */
	private static long diffMillis(long time) {
		return System.currentTimeMillis() - time;
	}

	/**
	 * @return the value of user.
	 */
	public static String getUser() {
		return user;
	}

	/**
	 * @return the value of pass.
	 */
	public static String getPass() {
		return pass;
	}

	/**
	 * @param ip
	 * @return the last URI requested by the user. {@value null} otherwise.
	 */
	public static String getPrevUri(String ip) {
		String res = null;
		for (String key : authorized.keySet()) {
			if (ip.equals(key)) {
				res = prevUris.get(key);
			}
		}
		return res;
	}

	/**
	 * It sets the last URI requested by the user.
	 * @see java.util.HashMap#put(Object, Object)
	 * 
	 * @param ip
	 * @param prevUri
	 */
	public static void setPrevUri(String ip, String prevUri) {
		prevUris.put(ip, prevUri);
	}

	/**
	 * @param ip
	 * @return the time associated to the IP. {@value -1} otherwise.
	 */
	public synchronized static long getLoginTime(String ip) {
		long res = -1;
		for (String key : authorized.keySet()) {
			if (ip.equals(key)) {
				res = authorized.get(key);
			}
		}
		return res;
	}

	/**
	 * It updates the time associated to the IP.
	 * 
	 * @param ip
	 */
	private synchronized static void updateLoginTime(String ip) {
		for (String key : authorized.keySet()) {
			if (ip.equals(key)) {
				authorized.replace(key, System.currentTimeMillis());
			}
		}
	}

	/**
	 * @param sock
	 * @return the {@link SocketAddress} ip.
	 */
	private static String getIp(SocketAddress sock) {
		return sock.toString().split(":")[0];
	}

}
