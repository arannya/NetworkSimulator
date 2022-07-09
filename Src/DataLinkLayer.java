import java.util.*;
import java.net.*;
import java.io.*;

//+++++++++++++++++Class: DataLinkLayer+++++++++++++++++++++++++++++++++
class DataLinkLayer{
	SimPhy simPhy;		
	byte srcMac;
	
	Buffer<ByteArray> phy2dll;
	Buffer<ByteArray> dll2phy;
	Buffer<ByteArray> dll2nl;
	Buffer<ByteArray> nl2dll;	
	
	public static int frameBufferSize=10;
	public static int packetBufferSize=10;
	public static byte BROADCAST_MAC=(byte)255;
	
	String portId="";
	//=======================================================		
	DataLinkLayer(String deviceId, String pId, String otherId, byte mac, Buffer<ByteArray> d, Buffer<ByteArray> n){		
	//DataLinkLayer(String deviceId, String pId, String otherId, byte mac, Buffer<ByteArray> d, Buffer<ByteArray> n, boolean isUp){			
		portId=pId;
		phy2dll=new Buffer<ByteArray>("Phy2Dll Buffer",1);
		dll2phy=new Buffer<ByteArray>("Dll2Phy Buffer",1);
				
		simPhy=new SimPhy(deviceId, pId, otherId, phy2dll, dll2phy);			
		
		srcMac=mac;
		dll2nl=d;
		nl2dll=n;
		
		//System.out.println("Interface Mac: "+mac);
		new DllSend(this);
		new DllReceive(this);
	}
	//----------------------------------------------------------------------------------
	public void setPortStatus(boolean s){simPhy.setPortStatus(s);}
	//----------------------------------------------------------------------------------	
	public ByteArray receiveFromNl(){
		try{
				synchronized(nl2dll){
					if (nl2dll.empty()) nl2dll.wait();	
					ByteArray p=nl2dll.get();
					nl2dll.notify();
					return p;
				}												
		}
		catch(Exception e){
			e.printStackTrace();
		}
		return null;
	}
	////----------------------------------------------------------------------------------
	public void sendToPhy(Frame f){		
		try{
				synchronized(dll2phy){
					if (dll2phy.full()) dll2phy.wait();	
					dll2phy.store(new ByteArray(f.getBytes()));
					dll2phy.notify();
				}											
		}
		catch(Exception e){
			e.printStackTrace();
		}		
	}
	////----------------------------------------------------------------------------------
	public void sendToNl(Packet p){
		
		try{
				synchronized(dll2nl){
					if (dll2nl.full()) dll2nl.wait();
					
					//dll of host directly stores the packet whereas dll of router also stores the interface received on										
					ByteArray b;
					if(portId.compareTo("")==0){b=new ByteArray(p.getBytes());}
					else{
						b=new ByteArray((p.getBytes()).length+1);
						b.setByteVal(0,(byte)Integer.parseInt(portId));
						b.setAt(1,p.getBytes());
					}
					dll2nl.store(b);
					dll2nl.notify();
				}											
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
	////----------------------------------------------------------------------------------
	public Frame receiveFromPhy(){		
		try{
				synchronized(phy2dll){
					if (phy2dll.empty()) phy2dll.wait();	
					ByteArray b=phy2dll.get();
					Frame f;					
					if(portId.compareTo("")==0){f=new Frame(b.getBytes());}
					else{
						f=new Frame(b.getAt(1,b.getSize()-1));
					}
					phy2dll.notify();
					return f;	
				}											
		}
		catch(Exception e){
			e.printStackTrace();
		}
		return null;
	}	
	
	////----------------------------------------------------------------------------------
	void receiveHandler(Frame r){
		//System.out.println("Dst Mac:"+r.getDstMac()+" Src: "+srcMac);
		if(r.getDstMac()==srcMac || r.getDstMac()==BROADCAST_MAC){
			if (r.hasCheckSumError()){
				System.out.println("\tChecksum Erron in Frame: "+r.getString()+"\n");			
				return;
			}
			else sendToNl(new Packet(r.getPayload()));			
		}
		else System.out.println("\tMac Mismatch. Dropping Frame: "+r.getString()+"\n");			
		
	}
	//----------------------------------------------------------------------------------
	void sendHandler(Packet p, int dstMac){		
		send_frame(p, dstMac);	/* transmit the frame */		
	}	
	void send_frame(Packet p, int dstMac){		  
	  Frame s=new Frame(srcMac, dstMac, p.getBytes());	
	  sendToPhy(s);	/* transmit the frame */	  
	}
	////----------------------------------------------------------------------------------
	private class DllSend extends Thread{
		DataLinkLayer dll;		
		public DllSend(DataLinkLayer d){
			dll=d;			
			start();
		}
		public void run(){
			try{
				while (true){
					ByteArray p=dll.receiveFromNl();
					synchronized(dll){
						dll.sendHandler(new Packet(p.getAt(1,p.getSize()-1)), p.getByteVal(0));	//the second argument is the mac address				
					}				
				}
			}
			catch(Exception e){
				e.printStackTrace();
			}
		}
	}	
	////----------------------------------------------------------------------------------
	private class DllReceive extends Thread{
		DataLinkLayer dll;		
		public DllReceive(DataLinkLayer d){
			dll=d;			
			start();
		}
		public void run(){
			try{
				while(true){
					Frame r=dll.receiveFromPhy();
					synchronized(dll){
					//from protocol
						dll.receiveHandler(r);						
					}
				}
			}
			catch(Exception e){}
		}
	}		
}
