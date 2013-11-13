import java.net.*;
import java.io.*;
import java.util.*;

public class idsLoadBalancer extends Thread
{
   private ServerSocket serverSocket;
   
   public idsLoadBalancer(int iPort, int iTimeout) throws IOException
   {
     if (iPort == 0) {
       iPort = 18385;
     }
     serverSocket = new ServerSocket(iPort);
     
     if (iTimeout > 0) {
       serverSocket.setSoTimeout(iTimeout * 1000);
     }
   }

   public void run()
   {
      while(true)
      {
         try
         {
           StringBuffer sRequest = new StringBuffer();
           String sInputLine = "";
           int iResult = 0;
           int iRequestLength = 0;

           System.out.println("Waiting for indesign server requests on port " +
           serverSocket.getLocalPort() + " with timeout " + serverSocket.getSoTimeout());
           Socket server = serverSocket.accept();
           System.out.println("Request from "
                 + server.getRemoteSocketAddress());

           InputStream oInputStream = server.getInputStream();

           BufferedReader in
                     = new BufferedReader(new InputStreamReader(oInputStream));

           while ((sInputLine = in.readLine()) != null) {
             if (sInputLine.length() == 0) {
               sRequest.append("\r\n");
               char[] aBytes = new char[iRequestLength];
               in.read(aBytes);
               sRequest.append(aBytes);
               break;
             }
             String[] aParts = sInputLine.split(" ");
             if (aParts[0].compareTo("Content-Length:") == 0) {
               iRequestLength = Integer.parseInt(aParts[1]);
             }
             if (aParts[0].compareTo("Expect:") == 0) {
               continue;
             }
             sRequest.append(sInputLine + "\r\n");
           }

           System.out.println("Request: " + sRequest);

           idsServerRequest request = new idsServerRequest(server, sRequest.toString());
           new Thread(request).start();

         }catch(SocketTimeoutException s)
         {
            System.out.println("Socket timed out!");
            break;
         }catch(EOFException e)
         {
           System.out.println("no data received");
         }catch(IOException e)
         {
            e.printStackTrace();
            System.out.println("Socket closed!");
            break;
         }
      }
   }
   public static void main(String [] args)
   {
     int iPort = 0;
     int iTimeout = 0;

     if (args.length > 0 && Integer.parseInt(args[0]) > 0) {
       iPort = Integer.parseInt(args[0]);
     }

     if (args.length > 1 && Integer.parseInt(args[1]) > 0) {
       iTimeout = Integer.parseInt(args[1]);
     }
      
     try
     {
        Thread t = new idsLoadBalancer(iPort, iTimeout);
        t.start();
     }catch(IOException e)
     {
        e.printStackTrace();
     }
   }
}
