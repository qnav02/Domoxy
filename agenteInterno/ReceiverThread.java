package agenteInterno;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import pm.ProxyMessage;

/**
 * This class sends a response through the pending connection made by a {@link Receiver} after contacting a device.
 * 
 *  @see {@link Receiver}
 */
public class ReceiverThread implements Runnable {

	private boolean secure;
	private String address;
	private String port;
	private String proxyAddress;
	private int proxyPort;
	private ProxyMessage pm;
	private Map<String, List<String>> headers;
	
	/**
	 * Constructor.
	 * 
	 * @param secure
	 * @param address
	 * @param port
	 * @param proxyAddress
	 * @param proxyPort
	 * @param pm
	 */
	public ReceiverThread(boolean secure, String address, String port, String proxyAddress, int proxyPort, ProxyMessage pm) {
		this.secure = secure;
		this.address = address;
		this.port = port;
		this.proxyAddress = proxyAddress;
		this.proxyPort = proxyPort;
		this.pm = pm;
	}

	@Override
	public void run() {
		try {
			/*------------------------------ device connection ------------------------------*/
			URL urlOgg = new URL("http://" + address + ":" + port + pm.getUri());
			HttpURLConnection connOgg = (HttpURLConnection) urlOgg.openConnection();
			connOgg.setRequestMethod(pm.getMethod());
			headers = connOgg.getHeaderFields();
			String resCode = String.valueOf(connOgg.getResponseCode());
			String resMsg = connOgg.getResponseMessage();
			String resHeaders = "";
			for(String s: headers.keySet()){
				if(s != null){
					if(!s.contains("Content-Length")){
						resHeaders += s + ": " + headers.get(s).toString().replace("[", "").replace("]", "") + "@#@"; 
					}
				}
			}
			resHeaders += "@@@@";
			String resBody = "";
			BufferedReader inFromObj = new BufferedReader(new InputStreamReader(connOgg.getInputStream()));
			BufferedImage image = null;
			if(resHeaders.contains("image/")){
				String imgType = getImgType(resHeaders);
				image = ImageIO.read(urlOgg);
				
				/*------------------------------ send response to the proxy server (IMAGE)------------------------------*/
				String sentence;
				if(secure){
					SSLSocketFactory ssf = (SSLSocketFactory) SSLSocketFactory.getDefault();
					SSLSocket ssc = (SSLSocket) ssf.createSocket(proxyAddress, proxyPort);
					ssc.startHandshake();
					DataOutputStream outToServer = new DataOutputStream(ssc.getOutputStream());
					ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
					ImageIO.write(image, imgType, byteArrayOutputStream);
					int size = byteArrayOutputStream.size();
					sentence = "PUT /response/" + pm.getId_thread() + " HTTP/1.1\r\n\r\n";
					sentence += "HTTP/1.1 " + resCode + " " + resMsg + "\r\n\r\n@@@@" + resHeaders + " ";
					outToServer.writeBytes(sentence + '\n');
					outToServer.writeBytes("&IMAGE&" + '\n');
					outToServer.writeBytes(String.valueOf(size) + '\n');
					ImageIO.write(image, imgType, outToServer);
					ssc.close();
				}
				else{
					Socket clientSocket = new Socket(proxyAddress, proxyPort);
					DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
					ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
					ImageIO.write(image, imgType, byteArrayOutputStream);
					int size = byteArrayOutputStream.size();
					sentence = "PUT /response/" + pm.getId_thread() + " HTTP/1.1\r\n\r\n";
					sentence += "HTTP/1.1 " + resCode + " " + resMsg + "\r\n\r\n@@@@" + resHeaders + " ";
					outToServer.writeBytes(sentence + '\n');
					outToServer.writeBytes("&IMAGE&" + '\n');
					outToServer.writeBytes(String.valueOf(size) + '\n');
					ImageIO.write(image, imgType, outToServer);
					clientSocket.shutdownInput();
					clientSocket.close();
				}
				/*------------------------------ end (IMAGE)------------------------------*/
			}
			else{
				String s = inFromObj.readLine();
				while (s != null) {
					resBody += s + "@#@";
					s = inFromObj.readLine();
				}
				/*------------------------------ send response to the proxy server ------------------------------*/
				String sentence;
				if(secure){
					SSLSocketFactory ssf = (SSLSocketFactory) SSLSocketFactory.getDefault();
					SSLSocket ssc = (SSLSocket) ssf.createSocket(proxyAddress, proxyPort);
					ssc.startHandshake();
					DataOutputStream outToServer = new DataOutputStream(ssc.getOutputStream());
					sentence = "PUT /response/" + pm.getId_thread() + " HTTP/1.1\r\n\r\n";
					sentence += "HTTP/1.1 " + resCode + " " + resMsg + "\r\n\r\n@@@@" + resHeaders + resBody;
					sentence += "\r\n&EOF&";
					outToServer.writeBytes(sentence + '\n');
					outToServer.flush();
					BufferedReader inFromServer = new BufferedReader(new InputStreamReader(ssc.getInputStream()));
					System.out.println(inFromServer.readLine());
					ssc.close();
				}
				else{
					Socket clientSocket = new Socket(proxyAddress, proxyPort);
					DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
					sentence = "PUT /response/" + pm.getId_thread() + " HTTP/1.1\r\n\r\n";
					sentence += "HTTP/1.1 " + resCode + " " + resMsg + "\r\n\r\n@@@@" + resHeaders + resBody;
					sentence += "\r\n&EOF&";
					outToServer.writeBytes(sentence + '\n');
					outToServer.flush();
					BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
					System.out.println(inFromServer.readLine());
					clientSocket.close();
				}
				/*------------------------------ end ------------------------------*/
			}
			inFromObj.close();
			connOgg.disconnect();
			/*------------------------------ end device connection ------------------------------*/

		} catch (FileNotFoundException fnfe) {
			System.out.println("FILE NOT FOUND");
			// send response
			try {
				String sentence;
				if(secure){
					SSLSocketFactory ssf = (SSLSocketFactory) SSLSocketFactory.getDefault();
					SSLSocket ssc = (SSLSocket) ssf.createSocket(proxyAddress, proxyPort);
					ssc.startHandshake();
					DataOutputStream outToServer = new DataOutputStream(ssc.getOutputStream());
					sentence = "PUT /response/" + pm.getId_thread() + " HTTP/1.1\r\n\r\n";
					sentence += "HTTP/1.1 404 NOT FOUND" + "@#@\r\n@@@@NOT FOUND@@@@NOT FOUND";
					sentence += "\r\n&EOF&";
					outToServer.writeBytes(sentence + '\n');
					System.out.println("-----------------------\n" + sentence + "\n-----------------------");
					outToServer.flush();
					BufferedReader inFromServer = new BufferedReader(new InputStreamReader(ssc.getInputStream()));
					inFromServer.readLine();
					ssc.close();
				}
				else{
					Socket clientSocket = new Socket(proxyAddress, proxyPort);
					DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
					sentence = "PUT /response/" + pm.getId_thread() + " HTTP/1.1\r\n\r\n";
					sentence += "HTTP/1.1 404 NOT FOUND" + "@#@\r\n@@@@NOT FOUND@@@@NOT FOUND";
					sentence += "\r\n&EOF&";
					outToServer.writeBytes(sentence + '\n');
					System.out.println("-----------------------\n" + sentence + "\n-----------------------");
					outToServer.flush();
					BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
					inFromServer.readLine();
					clientSocket.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		} catch (ConnectException e) {
			System.out.println("ERROR: " + e.getMessage());
		} catch (MalformedURLException e) {
			System.out.println("ERROR: " + e.getMessage());
		} catch (IOException e) {
			System.out.println("ERROR: " + e.getMessage());
		}
	}
	
	/**
	 * @param headers
	 * @return the image type in {@link String} format.
	 */
	private String getImgType(String headers) {
		String imgType = null;
		if(headers.contains("png")){
			imgType = "png";
		}
		if(headers.contains("jpeg")){
			imgType = "jpeg";
		}
		if(headers.contains("jpg")){
			imgType = "jpg";
		}
		if(headers.contains("gif")){
			imgType = "gif";
		}	
		return imgType;
	}
}
