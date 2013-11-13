import java.net.*;
import java.io.*;

public class idsServerRequest implements Runnable
{
   private Socket server;
   private String sRequest;
   
   public idsServerRequest(Socket server, String sRequest) throws IOException
   {
     this.server = server;
     this.sRequest = sRequest;
   }

   public void run()
   {
     String sInputLine = "";
     StringBuffer sResult = new StringBuffer();
     int iRequestLength = 0;

     try {
       Socket client = new Socket("localhost", 18385);
       System.out.println("Just connected to "
                 + client.getRemoteSocketAddress());

       OutputStream oOutputStream = client.getOutputStream();
       System.out.println("");
       System.out.println("sending request to server...");

       DataOutputStream out =
                new DataOutputStream(oOutputStream);
       out.writeBytes(this.sRequest);
       InputStream oInputStream = client.getInputStream();
       BufferedReader in
          = new BufferedReader(new InputStreamReader(oInputStream));
       System.out.println("");

       while ((sInputLine = in.readLine()) != null) {
         if (sInputLine.length() == 0) {
           sResult.append("\r\n");
           char[] aBytes = new char[iRequestLength];
           in.read(aBytes);
           sResult.append(aBytes);
           break;
         }
         String[] aParts = sInputLine.split(" ");
         if (aParts[0].compareTo("Content-Length:") == 0) {
           iRequestLength = Integer.parseInt(aParts[1]);
         }
         sResult.append(sInputLine + "\r\n");
       }

       System.out.println("InDesign Server says: " + sResult);

       client.close();
     }
     catch (IOException e) {
       e.printStackTrace();
       System.out.println("error sending request");
     }
     finally {
       try {
         System.out.println("returning result...");
         OutputStream oOutputStream = server.getOutputStream();
         DataOutputStream out =
                new DataOutputStream(oOutputStream);
         out.writeBytes(sResult.toString());
         this.server.close();
       }
       catch (IOException e) {
         System.out.println("error closing server");
       }
     }
   }
}
