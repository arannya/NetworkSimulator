import java.util.*;
import java.net.*;
import java.io.*;

//+++++++++++++++++Class: SimHost+++++++++++++++++++++++++++++++++
class SimHost{

	IpAddress ipAddr;
	int mask;
	IpAddress gateway;
	String otherEnd;
	byte macAddress;
	Hashtable arpTable;
	
	public DataLinkLayer dll;	
	public BufferedReader br;
	
	//boolean enable;
	
	Buffer<ByteArray> dll2nl;
	Buffer<ByteArray> nl2dll;
	
	//=======================================================
	public void loadParameters(String deviceId) throws Exception{		
		BufferedReader br=new BufferedReader(new FileReader("..//Config//Config.txt"));		
		String line;
		while((line=br.readLine())!=null){
			String[] tokens= line.split(":");			
			if(tokens[0].compareTo(deviceId)==0){
				String paramName=tokens[1];
				if(paramName.compareTo("CONNECTSTO")==0){
					otherEnd=tokens[2];
					if (tokens.length>3) otherEnd=otherEnd+tokens[3];
				}
				else if(paramName.compareTo("IPADDRESS")==0){
					ipAddr=new IpAddress(tokens[2]);
					System.out.println("IP ADDRESS: "+ipAddr.getString());
				}
				else if(paramName.compareTo("SUBNETMASK")==0){
					mask=Integer.parseInt(tokens[2]);
					System.out.println("SUBNET MASK LENGTH: "+mask);
				}
				else if(paramName.compareTo("DEFAULTGATEWAY")==0){
					gateway=new IpAddress(tokens[2]);
					System.out.println("Default Gateway: "+gateway.getString());;
				}
				else if(paramName.compareTo("MACADDRESS")==0){
					macAddress=(byte)Integer.parseInt(tokens[2]);
					System.out.println("MAC ADDRESS: "+(int)((byte)macAddress & 0xFF));
				}				
			}
		}
	}
	//=======================================================
	public void loadArpTable() throws Exception{	
		arpTable=new Hashtable();
		BufferedReader br=new BufferedReader(new FileReader("..//Config//ArpTable.txt"));		
		String line;
		while((line=br.readLine())!=null){
			String[] tokens= line.split(":");
			IpAddress dst=new IpAddress(tokens[0]);
			if(ipAddr.sameSubnet(dst, mask)){
				arpTable.put(tokens[0],tokens[1]);
			}
		}
	}
	//=======================================================				
	public SimHost(String deviceId){
		try{
			loadParameters(deviceId);
			loadArpTable();
		}catch(Exception e){}
		
		br= new BufferedReader(new InputStreamReader(System.in));
		//enable=false;
		
		dll2nl=new Buffer<ByteArray>("Dll2Nl Buffer",1);
		nl2dll=new Buffer<ByteArray>("Nl2Dll Buffer",1);
		dll=new DataLinkLayer(deviceId, "",otherEnd, macAddress, dll2nl, nl2dll);							
		
		new NlSend(this);
		new NlReceive(this);
	}
	//=======================================================
	public Packet receiveFromDll(){
		try{
				synchronized(dll2nl){
					if (dll2nl.empty()) dll2nl.wait();	
					Packet p=new Packet((dll2nl.get()).getBytes());
					dll2nl.notify();
					return p;	
				}											
		}
		catch(Exception e){
			e.printStackTrace();
		}
		return null;
	}
	//=======================================================
	public void sendToDll(ByteArray p){		
		try{
				synchronized(nl2dll){
					if (nl2dll.full()) nl2dll.wait();	
					nl2dll.store(p);
					nl2dll.notify();
				}											
		}
		catch(Exception e){
			e.printStackTrace();
		}		
	}		
	//=======================================================
	int getMacFromArpTable(IpAddress ip){
	    String macAd=(String) arpTable.get(ip.getString());
		if (macAd!=null) return Integer.parseInt(macAd);
		else return -1;
	}
	//=======================================================
	private class NlSend extends Thread{
		SimHost nl;		
		public NlSend(SimHost n){
			nl=n;			
			start();
		}
		public void run(){
			try{			
				while(true){				
					String input=br.readLine();					
					int colon=input.indexOf(':');				
					IpAddress dstIp=new IpAddress(input.substring(0,colon));			
					String text=input.substring(colon+1);
					Packet p=new Packet(ipAddr, dstIp, text.getBytes());
					
					//System.out.println(ipAddr.getString()+"-------"+mask);
					int dstMac;
					if(ipAddr.sameSubnet(dstIp, mask)){ 
						dstMac=getMacFromArpTable(dstIp);
					}
					else dstMac=getMacFromArpTable(gateway);
					
					if(dstMac<0) System.out.println("MAC Address of Destination not found");
					else{
						//System.out.println("Destination Mac:"+dstMac);
						ByteArray temp=new ByteArray((p.getBytes()).length+1);
						temp.setByteVal(0,(byte)dstMac);
						temp.setAt(1,p.getBytes());
						nl.sendToDll(temp);						
					}
				}			
			}
			catch(Exception e){
				e.printStackTrace();
			}
		}
	}
	//=======================================================
	private class NlReceive extends Thread{
		SimHost nl;		
		public NlReceive(SimHost n){
			nl=n;			
			start();
		}
		public void run(){
			while(true){
				Packet p=nl.receiveFromDll();
				if(p.getDstIp().sameIp(ipAddr)) System.out.println("\tShowing Packet: "+p.getString()+"\n");
			}
		}
	}
	//=======================================================
	public static void main(String args[]) throws Exception{					
		String deviceId="Dafault";
		
		int argCount=args.length;		
		if (argCount>0)deviceId=args[0];
		
		SimHost simHost=new SimHost(deviceId);
		
		while(true){}
	}
	//=======================================================
}

