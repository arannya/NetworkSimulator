import java.net.*;
import java.io.*;
import java.util.*;

class ConnectionDaemon extends Thread{
	public Hashtable connections;
	public Hashtable mappings;		
	//public Connection con[];
	public int cCount;
	//=======================================================
	public static void main(String args[]) throws Exception{
		ConnectionDaemon cDaemon=new ConnectionDaemon();				
		cDaemon.join();
	}	
	//=======================================================
	public ConnectionDaemon(){		
		connections= new Hashtable();
		mappings=new Hashtable();
		//con=new Connection[50];
		start();
	}
	synchronized public void registerConnection(String deviceId, String otherEnd, Connection c){
		connections.put(deviceId, c);
		mappings.put(deviceId,otherEnd);
		mappings.put(otherEnd,deviceId);
		System.out.println("Connections Registered For "+deviceId+" to "+otherEnd);
	}
	synchronized public void registerConnection(String deviceId, Connection c){
		connections.put(deviceId, c);
		System.out.println("Connections Registered For "+deviceId);
	}
	public void sendData(String deviceId, byte[] term){
		Connection c=(Connection)connections.get(deviceId);
		String otherEnd=(String)mappings.get(deviceId);
		if(otherEnd!=null){
			Connection o=(Connection)connections.get(otherEnd);
			if(o!=null){
				System.out.println("Sending from "+deviceId+" to "+otherEnd);
				o.send(term);
			}
		}
	}
	//=======================================================
	public void run(){
		try{
			ServerSocket servSoc = new ServerSocket(9009);//server with port number					
			while(true){				
				Socket sock= servSoc.accept();
				//con[cCount]=new Connection(sock, this);
				new Connection(sock, this);
				cCount++;
			}			
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
	//=======================================================
	
}
//++++++++++++++++++++++++++++Connection Class+++++++++++++++++++
class Connection extends Thread{	
	String deviceId;
	Socket sock;	
	ConnectionDaemon cDaemon;	
	//BufferedReader br;	
	DataInputStream br;
	OutputStream bw;
	//PrintWriter bw;	
	//int logged=0;
	//=========================================================
	Connection(Socket sc, ConnectionDaemon cd) throws Exception{
		sock = sc;
		cDaemon=cd;				
		//br=new BufferedReader(new InputStreamReader(sock.getInputStream()));					
		br=new DataInputStream(sock.getInputStream());
		//bw=new PrintWriter(new OutputStreamWriter(sock.getOutputStream()),true);				
		bw=sock.getOutputStream();	
		
		start();
	}
	public void send(byte[] message){
		//bw.println(message);
		//bw.print(message); //handling special case
		SimPhy.writeStuffed(bw, message);
	}
	//=========================================================
	public void run(){
		try{
			//String temp=br.readLine();
			
			byte [] b=SimPhy.readDeStuffed(br);			
			String temp=new String(b);
			//System.out.println("String DeStuffed "+temp);
			//----------End of special case--------------	 */
			int colon=temp.indexOf(':');
			if(colon>=0){
				deviceId=temp.substring(0,colon);
				String otherEnd=temp.substring(colon+1);
				cDaemon.registerConnection(deviceId,otherEnd,this);
			}
			else{
				deviceId=temp;
				cDaemon.registerConnection(deviceId,this);
			}
			while(true){
				b=SimPhy.readDeStuffed(br);						
				
				Frame f=new Frame(b);				
				System.out.println("\tFrame Received on "+deviceId+ ": "+f.getString()+"\n");
				//System.out.println("Reading Data From: "+deviceId);
				
				cDaemon.sendData(deviceId, b);
			}
		}		
		catch(Exception e){
			e.printStackTrace();
		}		
	}
}
	