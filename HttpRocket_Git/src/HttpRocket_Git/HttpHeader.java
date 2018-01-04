package HttpRocket_Git;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Analyze HTTP Header Info
 *
 */
public final class HttpHeader {

    private List<String> header = new ArrayList<String>();

    private String method;
    private String host;
    private String port;

    public static final int MAXLINESIZE = 4096;

    public static final String METHOD_GET = "GET";
    public static final String METHOD_POST = "POST";
    public static final String METHOD_CONNECT = "CONNECT";

    private HttpHeader() {
    }

    /**
     * read header info from stream, this step should be placed after start
     * streaming and before read any data
     *
     * @param in
     * @return
     * @throws IOException
     */
    public static final HttpHeader readHeader(InputStream in) throws IOException {
        HttpHeader header = new HttpHeader();
        StringBuilder sb = new StringBuilder();
        //read the interaction protocol first
        char c = 0;
        while ((c = (char) in.read()) != '\n') {
            sb.append(c);
            if (sb.length() == MAXLINESIZE) { // won't take it if header is too long
                break;
            }
        }

        // continue if the request type could be clarified, else quit it
        if (header.addHeaderMethod(sb.toString()) != null) {
            do {
                sb = new StringBuilder();
                while ((c = (char) in.read()) != '\n') {
                    sb.append(c);
                    if (sb.length() == MAXLINESIZE) { // break if header is too long
                        break;
                    }
                }
                if (sb.length() > 1 && header.notTooLong()) { // if header is too long, drop the remains
                    header.addHeaderString(sb.substring(0, sb.length() - 1));
                } else {
                    break;
                }
            } while (true);
        }

        return header;
    }

    /**
     *
     * @param str
     */
    private void addHeaderString(String str) {
        str = str.replaceAll("\r", "");
        header.add(str);
        if (str.startsWith("Host")) { // read out host and port
            String[] hosts = str.split(":");
            host = hosts[1].trim();
            if (method.endsWith(METHOD_CONNECT)) {
                port = hosts.length == 3 ? hosts[2] : "443"; //443 is https default port number
            } else if (method.endsWith(METHOD_GET) || method.endsWith(METHOD_POST)) {
                port = hosts.length == 3 ? hosts[2] : "80"; //80 is http default port number
            }
        }
    }

    /**
     * find out the request mode
     *
     * @param str
     * @return
     */
    private String addHeaderMethod(String str) {
        str = str.replaceAll("\r", "");
        header.add(str);

        if (str.startsWith(METHOD_CONNECT)) {//https connection request relay
            method = METHOD_CONNECT;
        } else if (str.startsWith(METHOD_GET)) {//http GET request
            method = METHOD_GET;
        } else if (str.startsWith(METHOD_POST)) {//http POST request
            method = METHOD_POST;
        }
        return method;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (String str : header) {
            sb.append(str).append("\r\n");
        }
        sb.append("\r\n");
        return sb.toString();
    }

    public boolean notTooLong() {
        return header.size() <= 16;
    }

    public List<String> getHeader() {
        return header;
    }

    public void setHeader(List<String> header) {
        this.header = header;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

}
