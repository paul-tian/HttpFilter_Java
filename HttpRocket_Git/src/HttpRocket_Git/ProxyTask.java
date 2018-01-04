package HttpRocket_Git;

//import java.io.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serve as a proxy between client and server
 *
 */
public class ProxyTask implements Runnable {

    private Socket socketIn;
    private Socket socketOut;

    private long totalUpload = 0l; // Total Upload Bits
    private long totalDownload = 0l; // Total Download Bits

    static public String filterUrls = "";
    static public String filterKeys = ""; // TODO: keyword filtering
    static boolean picFilter = false;
    static boolean stopProgram = false;
    static boolean isHttpsAndHttp = true;

    public ProxyTask(Socket socket) {
        this.socketIn = socket;
    }

    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    private static final String AUTHORED = "HTTP/1.1 200 Connection established\r\n\r\n";
    private static final String SERVERERROR = "HTTP/1.1 500 Connection FAILED\r\n\r\n";
    //private static final String UNAUTHORED="HTTP/1.1 407 Unauthorized\r\n\r\n";

    @Override
    public void run() {
        StringBuilder builder = new StringBuilder();
        try {
            builder.append("\r\n").append("Request Time  ��" + sdf.format(new Date()));
            InputStream isIn = socketIn.getInputStream();
            OutputStream osIn = socketIn.getOutputStream();

            // read header from client, grab host and port
            HttpHeader header = HttpHeader.readHeader(isIn);

            // add request log
            builder.append("\r\n").append("From    Host  ��" + socketIn.getInetAddress());
            builder.append("\r\n").append("From    Port  ��" + socketIn.getPort());
            builder.append("\r\n").append("Proxy   Method��" + header.getMethod());
            builder.append("\r\n").append("Request Host  ��" + header.getHost());
            builder.append("\r\n").append("Request Port  ��" + header.getPort());

            // return error if host and port cannot be read out
            if (header.getHost() == null || header.getPort() == null) {
                osIn.write(SERVERERROR.getBytes());
                osIn.flush();
                return;
            }

            if (!isHttpsAndHttp) {
                if (header.getPort().equals("443")) {
                    System.out.println("port is 443");
                    osIn.write(SERVERERROR.getBytes());
                    osIn.flush();
                    return;
                }
            }

            if (stopProgram) {
                osIn.write(SERVERERROR.getBytes());
                osIn.flush();
                return;
            }

            // add URL filtering
            if (!filterUrls.equals("")) {
                System.out.println("start check URL filer.....");
                String urls_regx[] = filterUrls.split(";");
                for (int i = 0; i < urls_regx.length; i++) {
                    String temp = urls_regx[i].replace("*", "");
                    if (header.getHost().contains(temp)) {
                        osIn.write(SERVERERROR.getBytes());
                        osIn.flush();
                        return;
                    }
                }
            }

            // find host and port
            socketOut = new Socket(header.getHost(), Integer.parseInt(header.getPort()));
            socketOut.setKeepAlive(true);
            InputStream isOut = socketOut.getInputStream();
            OutputStream osOut = socketOut.getOutputStream();

            //don't know why but if not use a new thread to realay some problem will appear
            Thread ot = new DataSendThread(isOut, osIn);
            ot.start();
            if (header.getMethod().equals(HttpHeader.METHOD_CONNECT)) {
                // send connected signal to client
                osIn.write(AUTHORED.getBytes());
                osIn.flush();
            } else {
                // relay http request header
                byte[] headerData = header.toString().getBytes();
                totalUpload += headerData.length;
                osOut.write(headerData);
                osOut.flush();
            }

            // relay client request to server
            readForwardDate(isIn, osOut);
            // wait until the relay thread terminated
            ot.join();
        } catch (Exception e) {
            e.printStackTrace();
            if (!socketIn.isOutputShutdown()) {
                // if error status could be grab, return it
                try {
                    socketIn.getOutputStream().write(SERVERERROR.getBytes());
                } catch (IOException e1) {
                }
            }
        } finally {
            try {
                if (socketIn != null) {
                    socketIn.close();
                }
            } catch (IOException e) {
            }
            if (socketOut != null) {
                try {
                    socketOut.close();
                } catch (IOException e) {
                }
            }

            //record upload and download data bytes, last closed time and print
            builder.append("\r\n").append("Up    Bytes  ��" + totalUpload);
            builder.append("\r\n").append("Down  Bytes  ��" + totalDownload);
            builder.append("\r\n").append("Closed Time  ��" + sdf.format(new Date()));
            builder.append("\r\n");
            logRequestMsg(builder.toString());
        }
    }

    /**
     * @param msg
     *
     */
    private synchronized void logRequestMsg(String msg) {
        System.out.println(msg);
    }

    /**
     * read client's info and relay to server
     *
     * @param isIn
     * @param osOut
     */
    private void readForwardDate(InputStream isIn, OutputStream osOut) {
        byte[] buffer = new byte[4096];
        try {
            int len;
            while ((len = isIn.read(buffer)) != -1) {
                if (len > 0) {
                    osOut.write(buffer, 0, len);
                    osOut.flush();
                }
                totalUpload += len;
                if (socketIn.isClosed() || socketOut.isClosed()) {
                    break;
                }
            }
        } catch (Exception e) {
            try {
                socketOut.close(); // try to close connection with server and interrupt the read-blocking status of relay thread
            } catch (IOException e1) {
            }
        }
    }

    /**
     * return server's info to client
     *
     * @param isOut
     * @param osIn
     */
    class DataSendThread extends Thread {

        private InputStream isOut;
        private OutputStream osIn;

        DataSendThread(InputStream isOut, OutputStream osIn) { // isOut reading from server, osIn write to proxy

            this.isOut = isOut;
            this.osIn = osIn;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[4096];
            try {
                int len;
                while ((len = isOut.read(buffer)) != -1) {
                    if (len > 0) {
                        // logData(buffer, 0, len);
                        //osIn.write(buffer, 0, len);

                        String str = new String(buffer);
                        if (picFilter) {
                            if (str.contains("Content-Type:image/") || str.contains("Content-Type: image")) {
                                socketIn.getOutputStream().write(SERVERERROR.getBytes());
                                socketIn.getOutputStream().flush();
                                return;
                            }
                            osIn.flush();
                            totalDownload += len;
                        }

                        if (!filterKeys.equals("")) {
                            boolean flag = false;
                            System.out.println("start check Key filer.....");
                            String keys_regx[] = filterKeys.split(";");
                            for (int i = 0; i < keys_regx.length; i++) {
                                if (str.contains(keys_regx[i])) {
                                    flag = true;
                                    str = str.replaceAll(keys_regx[i], "XXX");
                                }
                            }
                            if (flag) {
                                buffer = str.getBytes();
                                len = str.getBytes().length;
                            }
                        }
                        osIn.write(buffer, 0, len);
                        osIn.flush();
                        totalDownload += len;
                    }

                    if (socketIn.isOutputShutdown() || socketOut.isClosed()) {
                        break;
                    }
                }
            } catch (Exception e) {
            }
        }
    }

}
