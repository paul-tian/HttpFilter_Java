package HttpRocket_Git;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SocketProxy implements Runnable {

    static final int listenPort = 10240;
    static boolean runflag = true;

    @Override
    public void run() {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
            ServerSocket serverSocket = new ServerSocket(listenPort);
            final ExecutorService tpe = Executors.newCachedThreadPool();
            System.out.println("Proxy Server Start At " + sdf.format(new Date()));
            System.out.println("listening port:" + listenPort + "����");
            System.out.println();
            System.out.println();
            Socket socket = null;
            while (true) {
                socket = null;
                try {
                    socket = serverSocket.accept();

                    socket.setKeepAlive(true);
                    // add into task list and wait for process
                    tpe.execute(new ProxyTask(socket));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(SocketProxy.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
