package web;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.UUID;

import javax.imageio.ImageIO;

import pm.ProxyMessage;

/**
 * This class analyzes the type of each incoming request: 
 * 		- POST /login:			it validates username and password provided.
 * 		- POST /configuration: 	it configures a resource by inserting it in the {@link ConnectionTable}.
 * 		- PUT /response:		it fills the answer in the {@link WaitTable}
 * 		- otherwise: 			it handles the REST methods and contacts the {@link Receiver}
 * 
 * @see {@link ConnectionTable}
 * @see {@link WaitTable}
 * @see {@link Receiver}
 */
public class ServiceThread implements Runnable {

	private Socket connSocket;
	private HashMap<String, WaitTable> waitHash;
	private ConnectionTable ct;
	private WaitTable wt;
	private ProxyMessage proxyMessage;
	private Lock lock;

	private final static String TMPDIR = "./tmp";

	/**
	 * Constructor.
	 * 
	 * @param connSocket
	 * @param waitHash
	 * @param ct
	 * @param lock
	 */
	public ServiceThread(Socket connSocket, HashMap<String, WaitTable> waitHash, ConnectionTable ct, Lock lock) {
		this.connSocket = connSocket;
		this.waitHash = waitHash;
		this.ct = ct;
		this.lock = lock;
	}

	@Override
	public void run() {
		System.out.println("new thread n. " + Thread.currentThread().getName());
		lock.changeLock(true); // accept is freed
		BufferedReader inFromClient;
		byte[] resSentence = null;
		try {
			DataOutputStream outToClient = new DataOutputStream(connSocket.getOutputStream());
			InputStream isr = connSocket.getInputStream();
			inFromClient = new BufferedReader(new InputStreamReader(isr));
			String clientSentence;
			if ((clientSentence = inFromClient.readLine()) != null) {
				System.out.println("Received: " + clientSentence);
				String parsedUri = uriParser(clientSentence);
				if (clientSentence.contains("POST /login")) {
					String params = "";
					int k = 0;
					while (true) {
						int ch = inFromClient.read();
						k = (k << 8) | ch;
						params += new String(new byte[] { (byte) k });
						if (params.contains("EOF")) {
							break;
						}
					}
					if (params.contains("user") && params.contains("password")) {
						String user = params.split("user=")[1].split("&")[0];
						String password = params.split("password=")[1].split("&")[0];
						if (user.equals(Server.getUser()) && password.equals(Server.getPass())) {
							Server.setLogin(connSocket.getRemoteSocketAddress());
						}
					}
					resSentence = ("HTTP/1.1 200 OK\r\n\r\n<html><head><meta http-equiv=\"refresh\" content=\"0; url="
							+ Server.getPrevUri(connSocket.getRemoteSocketAddress().toString().split(":")[0])
							+ "\"/></head></html>" + '\n').getBytes();
					outToClient.write(resSentence);
					System.out.println("to client: " + resSentence);
					outToClient.close();
				} else if (clientSentence.startsWith("POST /configuration")) {
					// CONFIGURATOR
					if(!clientSentence.contains("___IAA___")){
						String uri = (String) parsedUri.subSequence(14, parsedUri.toString().length());
						ct.insert(uri, connSocket);
						resSentence = ("HTTP/1.1 200 OK\r\n" + '\n').getBytes();
						outToClient.write(resSentence);
						System.out.println("to client: " + resSentence);
						outToClient.flush();
					}
					else{
						resSentence = ("HTTP/1.1 200 OK\r\n" + '\n').getBytes();
						outToClient.write(resSentence);
						System.out.println("to client: " + resSentence);
						outToClient.flush();
					}
					System.out.println(ct.toString());
				} else if (clientSentence.contains("PUT /response")) {
					// fill answer
					String idTemp = clientSentence.split("/")[2].split(" ")[0];
					System.out.println("id temp " + idTemp);
					String res = "";
					byte[] imageAr = new byte[0];
					while (!(clientSentence = inFromClient.readLine()).contains("&EOF&")) {
						if (clientSentence.contains("&IMAGE&")) {
							String imgType = getImgType(res);
							BufferedInputStream bisr = new BufferedInputStream(isr);
							int size = Integer.parseInt(inFromClient.readLine());
							System.out.println("SIZE:" + size);
							BufferedImage img = ImageIO.read(bisr);
							System.out.println("after read");
							while (img == null) {
								img = ImageIO.read(bisr);
							}
							imageAr = new byte[size];
							imageAr = ("imagezxcvbnmqwerty:" + idTemp + "." + imgType).getBytes();
							File dir = new File(TMPDIR);
							if (!(dir.exists())) {
								if (!dir.mkdir()) {
									throw new IOException("Cannot create temp folder!");
								}
							}
							File f = new File(TMPDIR + "/" + idTemp + "." + imgType);
							ImageIO.write(img, imgType, f);
							System.out.println("H: " + img.getHeight());
							System.out.println("W: " + img.getWidth());
							break;
						}
						res += clientSentence;
					}
					WaitTable wtTemp = new WaitTable();
					synchronized (waitHash) {
						for (String key : waitHash.keySet()) {
							if (idTemp.equals(key)) {
								wtTemp = waitHash.get(key);
							}
						}
					}
					String clientSentenceSplit = new String(res);
					clientSentenceSplit = clientSentenceSplit.replaceAll("@#@", "\\\r\\\n");
					if(clientSentenceSplit.contains("@@@@")){
						String http = clientSentenceSplit.split("@@@@")[0];
						String headers = clientSentenceSplit.split("@@@@")[1];
						String body = clientSentenceSplit.split("@@@@")[2].trim();
						res = http + "\r\n" + headers + "\r\n" + body;
						wtTemp.fillAnswer(idTemp, mergeArrays(res.getBytes(), imageAr));
						resSentence = ("HTTP/1.1 200 OK\r\n" + '\n').getBytes();
					}
					try {
						outToClient.write(resSentence);
					} catch (SocketException sex) {
						System.out.println("Image received");
					}
					System.out.println("to client: " + resSentence);
					outToClient.flush();
				} else {
					String prevUri = "";
					if(parsedUri.contains("favicon") || parsedUri.contains("null") || parsedUri.contains("login")){
						prevUri = "/";
					}
					else{
						prevUri = parsedUri;
					}
					Server.setPrevUri(connSocket.getRemoteSocketAddress().toString().split(":")[0], prevUri);
					if (Server.getLoginTime(connSocket.getRemoteSocketAddress().toString().split(":")[0]) != -1) {
						String uniqueID = UUID.randomUUID().toString();
						this.wt = new WaitTable();
						synchronized (waitHash) {
							waitHash.put(uniqueID, wt);
						}
						System.out.println(uniqueID);
						// proxy message
						String method = methodParser(clientSentence);
						proxyMessage = new ProxyMessage(method, parsedUri, uniqueID);
						// search in ct
						if (searchInConnTable(parsedUri, outToClient)) {
							System.out.println("found " + parsedUri);
							// insert in wait table
							wt.insert(uniqueID, null);
							synchronized (wt) {
								while (wt.getAnsw(uniqueID) == null) {
									System.out.println("while " + wt.getAnsw(uniqueID));
									System.out.println(parsedUri);
									try {
										wt.wait();
									} catch (InterruptedException e) {
										System.out.println("ERROR: wait failed!");
									}
								}
								resSentence = wt.getAnsw(uniqueID);
								String resSentStr = new String(resSentence);
								if (resSentStr.contains("imagezxcvbnmqwerty:")) {
									String imgName = resSentStr.split("imagezxcvbnmqwerty:")[1];
									String imgType = getImgType(resSentStr);
									System.out.println(imgType);
									resSentence = resSentStr.split("imagezxcvbnmqwerty:")[0].getBytes();
									outToClient.write(resSentence);
									File f = new File(TMPDIR + "/" + imgName);
									BufferedImage bImg = ImageIO.read(f);
									outToClient.flush();
									ImageIO.write(bImg, imgType, outToClient);
									f.delete();
								} else {
									outToClient.write(resSentence); // to browser
								}
								System.out.println("to client awake: " + resSentence);
								outToClient.flush();
								outToClient.close();
								synchronized (waitHash) {
									waitHash.remove(uniqueID);
									System.out.println(waitHash.values().size());
								}
							}
						} else {
							resSentence = ("HTTP/1.1 404 NOT FOUND\r\n" + '\n').getBytes();
							outToClient.write(resSentence);
							System.out.println("to client: " + resSentence);
							outToClient.flush();
						}
					} else {
						File f = new File("./login.html");
						long length = f.length();
						byte[] bytes = new byte[(int) (length * 1024)];
						InputStream in = new FileInputStream(f);
						int count;
						outToClient.writeBytes("HTTP/1.1 200 OK\r\n\r\n");
						while ((count = in.read(bytes)) > 0) {
							outToClient.write(bytes, 0, count);
						}
						in.close();
						outToClient.flush();
						outToClient.close();
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * @param clientSentence
	 * @return the URI.
	 */
	private String uriParser(String clientSentence) {
		String str = clientSentence.split(" ")[1];
		return str;
	}

	/**
	 * @param clientSentence
	 * @return the HTTP method.
	 */
	private String methodParser(String clientSentence) {
		String str = clientSentence.split(" ")[0];
		return str;
	}

	/**
	 * @param uri
	 * @param outToClient an {@link OutputStream}
	 * @return {@value true} if there is an opened connection with the internal agent and the request is forwarded successfully. {@value false} otherwise.
	 * 
	 * @see {@link ConnectionTable}
	 */
	private synchronized boolean searchInConnTable(String uri, OutputStream outToClient) {
		boolean found = false;
		Socket ctSock = this.ct.getSock(uri);
		if (ctSock != null) {
			try {
				OutputStream ctos = ctSock.getOutputStream();
				ctos.write((proxyMessage.getMethod() + "@#@" + proxyMessage.getUri() + "@#@"
						+ proxyMessage.getId_thread() + '\n').getBytes());
				ctos.flush();
				found = true;
			} catch (IOException e) {
				System.out.println("Cannot communicate with the internal agent!");
				try {
					String startUri = uri.split("/")[1];
					this.ct.remove("/" + startUri);
					System.out.println(uri);
					String response = "HTTP/1.1 404 NOT FOUND\r\n" + '\n';
					outToClient.write(response.toString().getBytes());
					outToClient.flush();
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		}
		return found;
	}

	/**
	 * @param one first bytes array.
	 * @param two second bytes array.
	 * @return a bytes array which is the combination of the two parameters.
	 */
	private byte[] mergeArrays(byte[] one, byte[] two) {
		byte[] combined = new byte[one.length + two.length];
		System.arraycopy(one, 0, combined, 0, one.length);
		System.arraycopy(two, 0, combined, one.length, two.length);
		return combined;
	}

	/**
	 * @param headers
	 * @return the image type in {@link String} format.
	 */
	private String getImgType(String headers) {
		String imgType = null;
		if (headers.contains("png")) {
			imgType = "png";
		}
		if (headers.contains("jpeg")) {
			imgType = "jpeg";
		}
		if (headers.contains("jpg")) {
			imgType = "jpg";
		}
		if (headers.contains("gif")) {
			imgType = "gif";
		}
		return imgType;
	}
}
