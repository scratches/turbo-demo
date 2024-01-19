package com.example.jsdemo;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.WebUtils;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ReadListener;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConnection;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.Part;

public class ServletUtils {

	static class ServerlessHttpServletResponse implements HttpServletResponse {

		private static final String DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";

		private String defaultCharacterEncoding = WebUtils.DEFAULT_CHARACTER_ENCODING;

		private String characterEncoding = this.defaultCharacterEncoding;

		private final ByteArrayOutputStream content = new ByteArrayOutputStream(1024);

		private final ServletOutputStream outputStream = new ResponseServletOutputStream();

		private String contentType;

		private int bufferSize = 4096;

		private Locale locale = Locale.getDefault();

		private final List<Cookie> cookies = new ArrayList<>();

		private final HttpHeaders headers = new HttpHeaders();

		private int status = HttpServletResponse.SC_OK;

		private ResponsePrintWriter writer;

		@Nullable
		private String errorMessage;

		@Override
		public void setCharacterEncoding(String characterEncoding) {
			this.characterEncoding = characterEncoding;
		}

		@Override
		public String getCharacterEncoding() {
			return this.characterEncoding;
		}

		@Override
		public ServletOutputStream getOutputStream() {
			return this.outputStream;
		}

		@Override
		public PrintWriter getWriter() throws UnsupportedEncodingException {
			if (this.writer == null) {
				Writer targetWriter = new OutputStreamWriter(this.content, getCharacterEncoding());
				this.writer = new ResponsePrintWriter(targetWriter);
			}
			return this.writer;
		}

		public byte[] getContentAsByteArray() {
			return this.content.toByteArray();
		}

		/**
		 * Get the content of the response body as a {@code String}, using the charset
		 * specified for the response by the application, either through
		 * {@link HttpServletResponse} methods or through a charset parameter on the
		 * {@code Content-Type}. If no charset has been explicitly defined, the
		 * {@linkplain #setDefaultCharacterEncoding(String) default character encoding}
		 * will be used.
		 *
		 * @return the content as a {@code String}
		 * @throws UnsupportedEncodingException if the character encoding is not
		 *                                      supported
		 * @see #getContentAsString(Charset)
		 * @see #setCharacterEncoding(String)
		 * @see #setContentType(String)
		 */
		public String getContentAsString() throws UnsupportedEncodingException {
			return this.content.toString(getCharacterEncoding());
		}

		public String getContentAsString(Charset fallbackCharset) throws UnsupportedEncodingException {
			return this.content.toString(getCharacterEncoding());
		}

		@Override
		public void setContentLength(int contentLength) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setContentLengthLong(long len) {
			// Ignore
		}

		@Override
		public void setContentType(@Nullable String contentType) {
			this.contentType = contentType;
		}

		@Override
		@Nullable
		public String getContentType() {
			return this.contentType;
		}

		@Override
		public void setBufferSize(int bufferSize) {
			this.bufferSize = bufferSize;
		}

		@Override
		public int getBufferSize() {
			return this.bufferSize;
		}

		@Override
		public void flushBuffer() {
		}

		@Override
		public void resetBuffer() {
			Assert.state(!isCommitted(), "Cannot reset buffer - response is already committed");
			this.content.reset();
		}

		@Override
		public boolean isCommitted() {
			return this.writer == null ? false : this.writer.commited;
		}

		@Override
		public void reset() {
			resetBuffer();
			this.characterEncoding = this.defaultCharacterEncoding;
			this.contentType = null;
			this.locale = Locale.getDefault();
			this.cookies.clear();
			this.headers.clear();
			this.status = HttpServletResponse.SC_OK;
			this.errorMessage = null;
		}

		@Override
		public void setLocale(@Nullable Locale locale) {
			if (locale == null) {
				return;
			}
			this.locale = locale;
			this.headers.add(HttpHeaders.CONTENT_LANGUAGE, locale.toLanguageTag());
		}

		@Override
		public Locale getLocale() {
			return this.locale;
		}

		// ---------------------------------------------------------------------
		// HttpServletResponse interface
		// ---------------------------------------------------------------------

		@Override
		public void addCookie(Cookie cookie) {
			throw new UnsupportedOperationException();
		}

		@Nullable
		public Cookie getCookie(String name) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean containsHeader(String name) {
			return this.headers.containsKey(name);
		}

		/**
		 * Return the names of all specified headers as a Set of Strings.
		 * <p>
		 * As of Servlet 3.0, this method is also defined in
		 * {@link HttpServletResponse}.
		 *
		 * @return the {@code Set} of header name {@code Strings}, or an empty
		 *         {@code Set} if none
		 */
		@Override
		public Collection<String> getHeaderNames() {
			return this.headers.keySet();
		}

		/**
		 * Return the primary value for the given header as a String, if any. Will
		 * return the first value in case of multiple values.
		 * <p>
		 * As of Servlet 3.0, this method is also defined in
		 * {@link HttpServletResponse}. As of Spring 3.1, it returns a stringified value
		 * for Servlet 3.0 compatibility. Consider using {@link #getHeaderValue(String)}
		 * for raw Object access.
		 *
		 * @param name the name of the header
		 * @return the associated header value, or {@code null} if none
		 */
		@Override
		@Nullable
		public String getHeader(String name) {
			return this.headers.containsKey(name) ? this.headers.get(name).get(0) : null;
		}

		/**
		 * Return all values for the given header as a List of Strings.
		 * <p>
		 * As of Servlet 3.0, this method is also defined in
		 * {@link HttpServletResponse}. As of Spring 3.1, it returns a List of
		 * stringified values for Servlet 3.0 compatibility. Consider using
		 * {@link #getHeaderValues(String)} for raw Object access.
		 *
		 * @param name the name of the header
		 * @return the associated header values, or an empty List if none
		 */
		@Override
		public List<String> getHeaders(String name) {
			if (!this.headers.containsKey(name)) {
				return Collections.emptyList();
			}
			return this.headers.get(name);
		}

		/**
		 * Return the primary value for the given header, if any.
		 * <p>
		 * Will return the first value in case of multiple values.
		 *
		 * @param name the name of the header
		 * @return the associated header value, or {@code null} if none
		 */
		@Nullable
		public Object getHeaderValue(String name) {
			return this.headers.containsKey(name) ? this.headers.get(name).get(0) : null;
		}

		/**
		 * The default implementation returns the given URL String as-is.
		 * <p>
		 * Can be overridden in subclasses, appending a session id or the like.
		 */
		@Override
		public String encodeURL(String url) {
			return url;
		}

		/**
		 * The default implementation delegates to {@link #encodeURL}, returning the
		 * given URL String as-is.
		 * <p>
		 * Can be overridden in subclasses, appending a session id or the like in a
		 * redirect-specific fashion. For general URL encoding rules, override the
		 * common {@link #encodeURL} method instead, applying to redirect URLs as well
		 * as to general URLs.
		 */
		@Override
		public String encodeRedirectURL(String url) {
			return encodeURL(url);
		}

		@Override
		public void sendError(int status, String errorMessage) throws IOException {
			Assert.state(!isCommitted(), "Cannot set error status - response is already committed");
			this.status = status;
			this.errorMessage = errorMessage;
		}

		@Override
		public void sendError(int status) throws IOException {
			Assert.state(!isCommitted(), "Cannot set error status - response is already committed");
			this.status = status;
		}

		@Override
		public void sendRedirect(String url) throws IOException {
			Assert.state(!isCommitted(), "Cannot send redirect - response is already committed");
			Assert.notNull(url, "Redirect URL must not be null");
			setHeader(HttpHeaders.LOCATION, url);
			setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
		}

		@Nullable
		public String getRedirectedUrl() {
			return getHeader(HttpHeaders.LOCATION);
		}

		@Override
		public void setDateHeader(String name, long value) {
			this.headers.set(name, formatDate(value));
		}

		@Override
		public void addDateHeader(String name, long value) {
			this.headers.add(name, formatDate(value));
		}

		private String formatDate(long date) {
			return newDateFormat().format(new Date(date));
		}

		private DateFormat newDateFormat() {
			SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT, Locale.US);
			return dateFormat;
		}

		@Override
		public void setHeader(String name, @Nullable String value) {
			this.headers.set(name, value);
		}

		@Override
		public void addHeader(String name, @Nullable String value) {
			this.headers.add(name, value);
		}

		@Override
		public void setIntHeader(String name, int value) {
			this.headers.set(name, String.valueOf(value));
		}

		@Override
		public void addIntHeader(String name, int value) {
			this.headers.add(name, String.valueOf(value));
		}

		@Override
		public void setStatus(int status) {
			if (!this.isCommitted()) {
				this.status = status;
			}
		}

		@Override
		public int getStatus() {
			return this.status;
		}

		@Nullable
		public String getErrorMessage() {
			return this.errorMessage;
		}

		/**
		 * Inner class that adapts the ServletOutputStream to mark the response as
		 * committed once the buffer size is exceeded.
		 */
		private class ResponseServletOutputStream extends ServletOutputStream {

			private WriteListener listener;

			@Override
			public boolean isReady() {
				return true;
			}

			@Override
			public void setWriteListener(WriteListener writeListener) {
				if (writeListener != null) {
					try {
						writeListener.onWritePossible();
					} catch (IOException e) {
						// log.error("Output stream is not writable", e);
					}

					listener = writeListener;
				}
			}

			@Override
			public void write(int b) throws IOException {
				try {
					content.write(b);
				} catch (Exception e) {
					if (listener != null) {
						listener.onError(e);
					}
				}
			}

			@Override
			public void close() throws IOException {
				super.close();
				flushBuffer();
			}
		}

		private class ResponsePrintWriter extends PrintWriter {

			private boolean commited;

			ResponsePrintWriter(Writer out) {
				super(out, true);
			}

			@Override
			public void write(char[] buf, int off, int len) {
				super.write(buf, off, len);
				super.flush();
				this.commited = true;
			}

			@Override
			public void write(String s, int off, int len) {
				super.write(s, off, len);
				super.flush();
				this.commited = true;
			}

			@Override
			public void write(int c) {
				super.write(c);
				super.flush();
				this.commited = true;
			}

			@Override
			public void flush() {
				super.flush();
				this.commited = true;
			}

			@Override
			public void close() {
				super.flush();
				super.close();
				this.commited = true;
			}
		}

	}

	static class ServerlessHttpServletRequest implements HttpServletRequest {

		private static final TimeZone GMT = TimeZone.getTimeZone("GMT");

		private static final BufferedReader EMPTY_BUFFERED_READER = new BufferedReader(new StringReader(""));

		/**
		 * Date formats as specified in the HTTP RFC.
		 *
		 * @see <a href="https://tools.ietf.org/html/rfc7231#section-7.1.1.1">Section
		 *      7.1.1.1 of RFC 7231</a>
		 */
		private static final String[] DATE_FORMATS = new String[] { "EEE, dd MMM yyyy HH:mm:ss zzz",
				"EEE, dd-MMM-yy HH:mm:ss zzz", "EEE MMM dd HH:mm:ss yyyy" };

		// ---------------------------------------------------------------------
		// ServletRequest properties
		// ---------------------------------------------------------------------

		private final Map<String, Object> attributes = new LinkedHashMap<>();

		@Nullable
		private String characterEncoding;

		@Nullable
		private byte[] content;

		@Nullable
		private ServletInputStream inputStream;

		@Nullable
		private BufferedReader reader;

		private final Map<String, String[]> parameters = new LinkedHashMap<>(16);

		/** List of locales in descending order. */
		private final LinkedList<Locale> locales = new LinkedList<>();

		private boolean asyncStarted = false;

		private boolean asyncSupported = true;

		private DispatcherType dispatcherType = DispatcherType.REQUEST;

		@Nullable
		private String authType;

		@Nullable
		private Cookie[] cookies;

		private final HttpHeaders headers = new HttpHeaders();

		@Nullable
		private String method;

		@Nullable
		private String pathInfo;

		private String contextPath = "";

		@Nullable
		private String queryString;

		@Nullable
		private String remoteUser;

		private final Set<String> userRoles = new HashSet<>();

		@Nullable
		private Principal userPrincipal;

		@Nullable
		private String requestedSessionId;

		@Nullable
		private String requestURI;

		private String servletPath = "";

		@Nullable
		private HttpSession session;

		private boolean requestedSessionIdValid = true;

		private boolean requestedSessionIdFromCookie = true;

		private boolean requestedSessionIdFromURL = false;

		private final MultiValueMap<String, Part> parts = new LinkedMultiValueMap<>();

		private AsyncContext asyncContext;

		public ServerlessHttpServletRequest(String method, String requestURI) {
			this.method = method;
			this.requestURI = requestURI;
			this.locales.add(Locale.ENGLISH);
		}

		@Override
		public String toString() {
			return "Method: " + this.method + ", RequestURI: " + this.requestURI;
		}

		/**
		 * Return the ServletContext that this request is associated with. (Not
		 * available in the standard HttpServletRequest interface for some reason.)
		 */
		@Override
		public ServletContext getServletContext() {
			return null;
		}

		@Override
		public Object getAttribute(String name) {
			return this.attributes.get(name);
		}

		@Override
		public Enumeration<String> getAttributeNames() {
			return Collections.enumeration(new LinkedHashSet<>(this.attributes.keySet()));
		}

		@Override
		@Nullable
		public String getCharacterEncoding() {
			return this.characterEncoding;
		}

		@Override
		public void setCharacterEncoding(@Nullable String characterEncoding) {
			this.characterEncoding = characterEncoding;
		}

		/**
		 * Set the content of the request body as a byte array.
		 * <p>
		 * If the supplied byte array represents text such as XML or JSON, the
		 * {@link #setCharacterEncoding character encoding} should typically be set as
		 * well.
		 *
		 * @see #setCharacterEncoding(String)
		 * @see #getContentAsByteArray()
		 * @see #getContentAsString()
		 */
		public void setContent(@Nullable byte[] content) {
			this.content = content;
			this.inputStream = null;
			this.reader = null;
		}

		/**
		 * Get the content of the request body as a byte array.
		 *
		 * @return the content as a byte array (potentially {@code null})
		 * @since 5.0
		 * @see #setContent(byte[])
		 * @see #getContentAsString()
		 */
		@Nullable
		public byte[] getContentAsByteArray() {
			return this.content;
		}

		/**
		 * Get the content of the request body as a {@code String}, using the configured
		 * {@linkplain #getCharacterEncoding character encoding}.
		 *
		 * @return the content as a {@code String}, potentially {@code null}
		 * @throws IllegalStateException        if the character encoding has not been
		 *                                      set
		 * @throws UnsupportedEncodingException if the character encoding is not
		 *                                      supported
		 * @since 5.0
		 * @see #setContent(byte[])
		 * @see #setCharacterEncoding(String)
		 * @see #getContentAsByteArray()
		 */
		@Nullable
		public String getContentAsString() throws IllegalStateException, UnsupportedEncodingException {

			if (this.content == null) {
				return null;
			}
			return new String(this.content, StandardCharsets.UTF_8);
		}

		@Override
		public int getContentLength() {
			return (this.content != null ? this.content.length : -1);
		}

		@Override
		public long getContentLengthLong() {
			return getContentLength();
		}

		public void setContentType(@Nullable String contentType) {
			this.headers.set(HttpHeaders.CONTENT_TYPE, contentType);
		}

		@Override
		@Nullable
		public String getContentType() {
			return this.headers.containsKey(HttpHeaders.CONTENT_TYPE)
					? this.headers.get(HttpHeaders.CONTENT_TYPE).get(0)
					: null;
		}

		@Override
		public ServletInputStream getInputStream() {
			InputStream stream = new ByteArrayInputStream(this.content);
			return new ServletInputStream() {

				boolean finished = false;

				@Override
				public int read() throws IOException {
					int readByte = stream.read();
					if (readByte == -1) {
						finished = true;
					}
					return readByte;
				}

				@Override
				public void setReadListener(ReadListener readListener) {
				}

				@Override
				public boolean isReady() {
					return !finished;
				}

				@Override
				public boolean isFinished() {
					return finished;
				}
			};
		}

		/**
		 * Set a single value for the specified HTTP parameter.
		 * <p>
		 * If there are already one or more values registered for the given parameter
		 * name, they will be replaced.
		 */
		public void setParameter(String name, String value) {
			setParameter(name, new String[] { value });
		}

		/**
		 * Set an array of values for the specified HTTP parameter.
		 * <p>
		 * If there are already one or more values registered for the given parameter
		 * name, they will be replaced.
		 */
		public void setParameter(String name, String... values) {
			Assert.notNull(name, "Parameter name must not be null");
			this.parameters.put(name, values);
		}

		/**
		 * Set all provided parameters <strong>replacing</strong> any existing values
		 * for the provided parameter names. To add without replacing existing values,
		 * use {@link #addParameters(java.util.Map)}.
		 */
		public void setParameters(Map<String, ?> params) {
			Assert.notNull(params, "Parameter map must not be null");
			params.forEach((key, value) -> {
				if (value instanceof String) {
					setParameter(key, (String) value);
				} else if (value instanceof String[]) {
					setParameter(key, (String[]) value);
				} else {
					throw new IllegalArgumentException(
							"Parameter map value must be single value " + " or array of type ["
									+ String.class.getName() + "]");
				}
			});
		}

		/**
		 * Add a single value for the specified HTTP parameter.
		 * <p>
		 * If there are already one or more values registered for the given parameter
		 * name, the given value will be added to the end of the list.
		 */
		public void addParameter(String name, @Nullable String value) {
			addParameter(name, new String[] { value });
		}

		/**
		 * Add an array of values for the specified HTTP parameter.
		 * <p>
		 * If there are already one or more values registered for the given parameter
		 * name, the given values will be added to the end of the list.
		 */
		public void addParameter(String name, String... values) {
			Assert.notNull(name, "Parameter name must not be null");
			String[] oldArr = this.parameters.get(name);
			if (oldArr != null) {
				String[] newArr = new String[oldArr.length + values.length];
				System.arraycopy(oldArr, 0, newArr, 0, oldArr.length);
				System.arraycopy(values, 0, newArr, oldArr.length, values.length);
				this.parameters.put(name, newArr);
			} else {
				this.parameters.put(name, values);
			}
		}

		/**
		 * Add all provided parameters <strong>without</strong> replacing any existing
		 * values. To replace existing values, use
		 * {@link #setParameters(java.util.Map)}.
		 */
		public void addParameters(Map<String, ?> params) {
			Assert.notNull(params, "Parameter map must not be null");
			params.forEach((key, value) -> {
				if (value instanceof String) {
					addParameter(key, (String) value);
				} else if (value instanceof String[]) {
					addParameter(key, (String[]) value);
				} else {
					throw new IllegalArgumentException(
							"Parameter map value must be single value " + " or array of type ["
									+ String.class.getName() + "]");
				}
			});
		}

		/**
		 * Remove already registered values for the specified HTTP parameter, if any.
		 */
		public void removeParameter(String name) {
			Assert.notNull(name, "Parameter name must not be null");
			this.parameters.remove(name);
		}

		/**
		 * Remove all existing parameters.
		 */
		public void removeAllParameters() {
			this.parameters.clear();
		}

		@Override
		@Nullable
		public String getParameter(String name) {
			Assert.notNull(name, "Parameter name must not be null");
			String[] arr = this.parameters.get(name);
			return (arr != null && arr.length > 0 ? arr[0] : null);
		}

		@Override
		public Enumeration<String> getParameterNames() {
			return Collections.enumeration(this.parameters.keySet());
		}

		@Override
		public String[] getParameterValues(String name) {
			Assert.notNull(name, "Parameter name must not be null");
			return this.parameters.get(name);
		}

		@Override
		public Map<String, String[]> getParameterMap() {
			return Collections.unmodifiableMap(this.parameters);
		}

		@Override
		public String getProtocol() {
			throw new UnsupportedOperationException();
		}

		@Override
		public String getScheme() {
			return "https";
		}

		public void setServerName(String serverName) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String getServerName() {
			return "spring-serverless-web-proxy";
		}

		public void setServerPort(int serverPort) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int getServerPort() {
			return 0;
		}

		@Override
		public BufferedReader getReader() throws UnsupportedEncodingException {
			if (this.reader != null) {
				return this.reader;
			} else if (this.inputStream != null) {
				throw new IllegalStateException(
						"Cannot call getReader() after getInputStream() has already been called for the current request");
			}

			if (this.content != null) {
				InputStream sourceStream = new ByteArrayInputStream(this.content);
				Reader sourceReader = (this.characterEncoding != null)
						? new InputStreamReader(sourceStream, this.characterEncoding)
						: new InputStreamReader(sourceStream);
				this.reader = new BufferedReader(sourceReader);
			} else {
				this.reader = EMPTY_BUFFERED_READER;
			}
			return this.reader;
		}

		public void setRemoteAddr(String remoteAddr) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String getRemoteAddr() {
			return "proxy";
		}

		public void setRemoteHost(String remoteHost) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String getRemoteHost() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setAttribute(String name, @Nullable Object value) {
			Assert.notNull(name, "Attribute name must not be null");
			if (value != null) {
				this.attributes.put(name, value);
			} else {
				this.attributes.remove(name);
			}
		}

		@Override
		public void removeAttribute(String name) {
			Assert.notNull(name, "Attribute name must not be null");
			this.attributes.remove(name);
		}

		/**
		 * Clear all of this request's attributes.
		 */
		public void clearAttributes() {
			this.attributes.clear();
		}

		/**
		 * Return the first preferred {@linkplain Locale locale} configured in this mock
		 * request.
		 * <p>
		 * If no locales have been explicitly configured, the default, preferred
		 * {@link Locale} for the <em>server</em> mocked by this request is
		 * {@link Locale#ENGLISH}.
		 * <p>
		 * In contrast to the Servlet specification, this mock implementation does
		 * <strong>not</strong> take into consideration any locales specified via the
		 * {@code Accept-Language} header.
		 *
		 * @see javax.servlet.ServletRequest#getLocale()
		 * @see #addPreferredLocale(Locale)
		 * @see #setPreferredLocales(List)
		 */
		@Override
		public Locale getLocale() {
			return this.locales.getFirst();
		}

		/**
		 * Return an {@linkplain Enumeration enumeration} of the preferred
		 * {@linkplain Locale locales} configured in this mock request.
		 * <p>
		 * If no locales have been explicitly configured, the default, preferred
		 * {@link Locale} for the <em>server</em> mocked by this request is
		 * {@link Locale#ENGLISH}.
		 * <p>
		 * In contrast to the Servlet specification, this mock implementation does
		 * <strong>not</strong> take into consideration any locales specified via the
		 * {@code Accept-Language} header.
		 *
		 * @see javax.servlet.ServletRequest#getLocales()
		 * @see #addPreferredLocale(Locale)
		 * @see #setPreferredLocales(List)
		 */
		@Override
		public Enumeration<Locale> getLocales() {
			return Collections.enumeration(this.locales);
		}

		/**
		 * Return {@code true} if the {@link #setSecure secure} flag has been set to
		 * {@code true} or if the {@link #getScheme scheme} is {@code https}.
		 *
		 * @see javax.servlet.ServletRequest#isSecure()
		 */
		@Override
		public boolean isSecure() {
			return false;
		}

		@Override
		public RequestDispatcher getRequestDispatcher(String path) {
			throw new UnsupportedOperationException();
		}

		public void setRemotePort(int remotePort) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int getRemotePort() {
			throw new UnsupportedOperationException();
		}

		public void setLocalName(String localName) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String getLocalName() {
			throw new UnsupportedOperationException();
		}

		public void setLocalAddr(String localAddr) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String getLocalAddr() {
			return "proxy";
		}

		@Override
		public int getLocalPort() {
			throw new UnsupportedOperationException();
		}

		@Override
		public AsyncContext startAsync() {
			throw new UnsupportedOperationException();
		}

		@Override
		public AsyncContext startAsync(ServletRequest request, @Nullable ServletResponse response) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean isAsyncStarted() {
			return this.asyncStarted;
		}

		@Override
		public boolean isAsyncSupported() {
			return this.asyncSupported;
		}

		public void setAsyncContext(@Nullable AsyncContext asyncContext) {
			this.asyncContext = asyncContext;
		}

		@Override
		@Nullable
		public AsyncContext getAsyncContext() {
			return this.asyncContext;
		}

		public void setDispatcherType(DispatcherType dispatcherType) {
			this.dispatcherType = dispatcherType;
		}

		@Override
		public DispatcherType getDispatcherType() {
			return this.dispatcherType;
		}

		public void setAuthType(@Nullable String authType) {
			this.authType = authType;
		}

		@Override
		@Nullable
		public String getAuthType() {
			return this.authType;
		}

		@Override
		@Nullable
		public Cookie[] getCookies() {
			return this.cookies;
		}

		@Override
		@Nullable
		public String getHeader(String name) {
			return this.headers.containsKey(name) ? this.headers.get(name).get(0) : null;
		}

		@Override
		public Enumeration<String> getHeaders(String name) {
			return Collections
					.enumeration(this.headers.containsKey(name) ? this.headers.get(name) : new LinkedList<>());
		}

		@Override
		public Enumeration<String> getHeaderNames() {
			return Collections.enumeration(this.headers.keySet());
		}

		public void setHeader(String name, @Nullable String value) {
			this.headers.set(name, value);
		}

		public void addHeader(String name, @Nullable String value) {
			this.headers.add(name, value);
		}

		public void addHeaders(MultiValueMap<String, String> headers) {
			this.headers.addAll(headers);
		}

		public void setHeaders(MultiValueMap<String, String> headers) {
			this.headers.clear();
			this.addHeaders(headers);
		}

		@Override
		public int getIntHeader(String name) {
			List<String> header = this.headers.get(name);
			if (!CollectionUtils.isEmpty(header) && header.size() == 1) {
				Object value = header.get(0);
				if (value instanceof Number) {
					return ((Number) value).intValue();
				} else if (value instanceof String) {
					return Integer.parseInt((String) value);
				} else if (value != null) {
					throw new NumberFormatException("Value for header '" + name + "' is not a Number: " + value);
				} else {
					return -1;
				}
			} else {
				return -1;
			}
		}

		@Override
		public long getDateHeader(String name) {
			List<String> header = this.headers.get(name);
			if (!CollectionUtils.isEmpty(header) && header.size() == 1) {
				Object value = header.get(0);
				if (value instanceof Date) {
					return ((Date) value).getTime();
				} else if (value instanceof Number) {
					return ((Number) value).longValue();
				} else if (value instanceof String) {
					return parseDateHeader(name, (String) value);
				} else if (value != null) {
					throw new IllegalArgumentException(
							"Value for header '" + name + "' is not a Date, Number, or String: " + value);
				} else {
					return -1L;
				}
			} else {
				return -1;
			}
		}

		private long parseDateHeader(String name, String value) {
			for (String dateFormat : DATE_FORMATS) {
				SimpleDateFormat simpleDateFormat = new SimpleDateFormat(dateFormat, Locale.US);
				simpleDateFormat.setTimeZone(GMT);
				try {
					return simpleDateFormat.parse(value).getTime();
				} catch (ParseException ex) {
					// ignore
				}
			}
			throw new IllegalArgumentException("Cannot parse date value '" + value + "' for '" + name + "' header");
		}

		public void setMethod(@Nullable String method) {
			this.method = method;
		}

		@Override
		@Nullable
		public String getMethod() {
			return this.method;
		}

		public void setPathInfo(@Nullable String pathInfo) {
			this.pathInfo = pathInfo;
		}

		@Override
		@Nullable
		public String getPathInfo() {
			return this.pathInfo;
		}

		@Override
		@Nullable
		public String getPathTranslated() {
			// return (this.pathInfo != null ? getRealPath(this.pathInfo) : null);
			return this.pathInfo;
		}

		public void setContextPath(String contextPath) {
			this.contextPath = contextPath;
		}

		@Override
		public String getContextPath() {
			return this.contextPath;
		}

		public void setQueryString(@Nullable String queryString) {
			this.queryString = queryString;
		}

		@Override
		@Nullable
		public String getQueryString() {
			return this.queryString;
		}

		public void setRemoteUser(@Nullable String remoteUser) {
			this.remoteUser = remoteUser;
		}

		@Override
		@Nullable
		public String getRemoteUser() {
			return this.remoteUser;
		}

		public void addUserRole(String role) {
			this.userRoles.add(role);
		}

		@Override
		public boolean isUserInRole(String role) {
			throw new UnsupportedOperationException();
		}

		public void setUserPrincipal(@Nullable Principal userPrincipal) {
			this.userPrincipal = userPrincipal;
		}

		@Override
		@Nullable
		public Principal getUserPrincipal() {
			return this.userPrincipal;
		}

		public void setRequestedSessionId(@Nullable String requestedSessionId) {
			this.requestedSessionId = requestedSessionId;
		}

		@Override
		@Nullable
		public String getRequestedSessionId() {
			return this.requestedSessionId;
		}

		public void setRequestURI(@Nullable String requestURI) {
			this.requestURI = requestURI;
		}

		@Override
		@Nullable
		public String getRequestURI() {
			return this.requestURI;
		}

		@Override
		public StringBuffer getRequestURL() {
			return new StringBuffer(this.requestURI);
		}

		public void setServletPath(String servletPath) {
			this.servletPath = servletPath;
		}

		@Override
		public String getServletPath() {
			return this.servletPath;
		}

		@Override
		@Nullable
		public HttpSession getSession(boolean create) {
			throw new UnsupportedOperationException();
		}

		@Override
		@Nullable
		public HttpSession getSession() {
			return getSession(true);
		}

		@Override
		public String changeSessionId() {
			throw new UnsupportedOperationException();
		}

		public void setRequestedSessionIdValid(boolean requestedSessionIdValid) {
			this.requestedSessionIdValid = requestedSessionIdValid;
		}

		@Override
		public boolean isRequestedSessionIdValid() {
			return this.requestedSessionIdValid;
		}

		public void setRequestedSessionIdFromCookie(boolean requestedSessionIdFromCookie) {
			this.requestedSessionIdFromCookie = requestedSessionIdFromCookie;
		}

		@Override
		public boolean isRequestedSessionIdFromCookie() {
			return this.requestedSessionIdFromCookie;
		}

		public void setRequestedSessionIdFromURL(boolean requestedSessionIdFromURL) {
			this.requestedSessionIdFromURL = requestedSessionIdFromURL;
		}

		@Override
		public boolean isRequestedSessionIdFromURL() {
			return this.requestedSessionIdFromURL;
		}

		@Override
		public boolean authenticate(HttpServletResponse response) throws IOException, ServletException {
			throw new UnsupportedOperationException();
		}

		@Override
		public void login(String username, String password) throws ServletException {
			throw new UnsupportedOperationException();
		}

		@Override
		public void logout() throws ServletException {
			this.userPrincipal = null;
			this.remoteUser = null;
			this.authType = null;
		}

		public void addPart(Part part) {
			this.parts.add(part.getName(), part);
		}

		@Override
		@Nullable
		public Part getPart(String name) throws IOException, ServletException {
			return this.parts.getFirst(name);
		}

		@Override
		public Collection<Part> getParts() throws IOException, ServletException {
			List<Part> result = new LinkedList<>();
			for (List<Part> list : this.parts.values()) {
				result.addAll(list);
			}
			return result;
		}

		@Override
		public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) throws IOException, ServletException {
			throw new UnsupportedOperationException();
		}

		@Override
		public String getRequestId() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public String getProtocolRequestId() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public ServletConnection getServletConnection() {
			// TODO Auto-generated method stub
			return null;
		}
	}

}
