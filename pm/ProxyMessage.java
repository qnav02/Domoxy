package pm;



import java.io.Serializable;

/**
 * @author aledani
 *
 */
public class ProxyMessage implements Serializable {
	
	private static final long serialVersionUID = 4337744562763643247L;
	private String method;
	private String uri;
	private String id_thread;

	/**
	 * @param uniqueID 
	 * 
	 */
	public ProxyMessage(String method, String uri, String id_thread) {
		this.method = method;
		this.uri = uri;
		this.id_thread = id_thread;
	}

	/**
	 * @return
	 */
	public String getUri() {
		return uri;
	}

	/**
	 * @return
	 */
	public String getId_thread() {
		return id_thread;
	}
	
	/**
	 * @return
	 */
	public String getMethod() {
		return method;
	}

}
