package org.lightj.session.exception;

/**
 * flow registration exception, flow type does not exist
 * @author binyu
 *
 */
public class NoSuchFlowException extends IllegalArgumentException {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5896578374323868697L;

	public NoSuchFlowException(String message) {
		super(message);
	}
    public NoSuchFlowException() {
	super();
    }

    public NoSuchFlowException(String message, Throwable cause) {
        super(message, cause);
    }
 
    public NoSuchFlowException(Throwable cause) {
        super(cause);
    }

}
