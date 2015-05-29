package ws.suid;

import java.io.IOException;
import java.io.PrintWriter;

import javax.ejb.EJB;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Listens for incoming requests for suid blocks and delegates them to the {@code SuidService}.
 * 
 * @see SuidService
 * 
 * @author Stijn de Witt [StijnDeWitt@hotmail.com]
 */
public class SuidServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	
	@EJB
	private SuidService suidService;

	/**
	 * Empty constructor, does nothing. 
	 */
	public SuidServlet() {
	}

	/**
	 * Initializes this servlet and the associated {@code SuidService}.
	 * 
	 * <p>This method reads the parameter {@code shard} from the servlet configuration and then 
	 * invokes {@code SuidService#init} with it.</p>
	 * 
	 * @see SuidService#init
	 */
	public void init() throws ServletException {
		suidService.init(getInt(getServletConfig().getInitParameter("shard"), 0));
	}

	/**
	 * Handles incoming GET requests.
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setHeader("Content-Type", "application/json");
		PrintWriter out = response.getWriter();
		Suid[] blocks = suidService.nextBlocks(getInt(request.getParameter("blocks"), 1));
		out.print("[");
		for (int i=0; i<blocks.length; i++) {
			out.print("\"" + blocks[i] + "\"" + (i < blocks.length -1 ? ", " : ""));
		}
		out.print("]");
	}

	/**
	 * Gets the Integer parameter from the given {@code value}.
	 * 
	 * @param value The string value of the parameter to get, may be {@code null}.
	 * @param def The default value to use if the parameter can't be found in the 
	 * 				request, or can't be parsed to an integer.
	 * @return The integer parameter, or {@code def} if the parameter could not be found or parsed.
	 */
	private int getInt(String value, int def) {
		int param = def;
		try {
			param = Integer.parseInt(value);
		}
		catch(NumberFormatException e) {
			// ignore
		}
		return param;
	}
}
