import java.util.*;
import java.net.*;
import java.io.*;
//+++++++++++++++++Class: MacTable+++++++++++++++++++++++++++++++++
class MacTable{
	int mac[];
	int port[];
	int size;
	int index;
	//-----------------------------------------------------------------	
	MacTable(int s){
		size=s;
		mac=new int[size];
		port=new int[size];
		index=0;
	}
	//-----------------------------------------------------------------	
	synchronized void put(int ma, int pid){
		if(get(ma)==0 && index<size){
			mac[index]=ma;
			port[index]=pid;
			index++;
		}
	}
	//-----------------------------------------------------------------	
	synchronized int get(int ma){
		for(int i=0; i<index; i++){
			if(mac[i]==ma) return port[i];
		}
		return 0;
	}
	//-----------------------------------------------------------------	
	synchronized void show(){
		System.out.println("\n--MAC Address Table--");
		for(int i=0; i<index;i++)
			System.out.println("Mac= "+mac[i]+" | Port= "+port[i]);
		System.out.println("-----------------------\n");
	}
}

//+++++++++++++++++Class: SimPort++++++++++++++++++++++++++++++++++++
class SimPort{	
	public String portId;
	Buffer<ByteArray> swMemory;	
	Buffer<ByteArray> portBuffer;	
	
	Socket sock;
	BufferedReader br;
	OutputStream bw;
	boolean hasConnection=false;
	
	String deviceId;
	String otherEnd=null;
	
	SimPhy simphy;
	//-----------------------------------------------------------------	
	SimPort(String dId, String pid, String other, Buffer<ByteArray> mem, Buffer<ByteArray> pbuf){		
		portId=pid;
		swMemory=mem;
		portBuffer=pbuf;
		deviceId=dId;		
		otherEnd=other;
		simphy=new SimPhy(dId, portId, other, swMemory, pbuf);	
	}
	public boolean connected(){return simphy.connected();}	
}
//+++++++++++++++++Class: SimSwitch++++++++++++++++++++++++++++++++++++
class SimSwitch extends Thread{
	public MacTable mactable;	
	public SimPort port[];
	Hashtable otherEnds=new Hashtable();
	public int portCount;	
	Buffer<ByteArray> swMemory;	
	Buffer<ByteArray>portBuffer[];
	
	public static int memSize=10;
	public static int macTableSize=10;
	
	/* public double SWITCH_PACKET_MISSING_RATE;// = 0.01;
	public double ERROR_RATE;// = 0.01;	  */
	
	
	BufferedReader br;
	//-----------------------------------------------------------------	
	public void loadParameters(String deviceId) throws Exception{		
		BufferedReader br=new BufferedReader(new FileReader("..\\Config\\Config.txt"));		
		String line;
		while((line=br.readLine())!=null){
			String[] tokens= line.split(":");			
			if(tokens[0].compareTo(deviceId)==0){
				String paramName=tokens[1];
				if(paramName.compareTo("NUMOFPORTS")==0){
					portCount=Integer.parseInt(tokens[2]);
				}
				else if(paramName.compareTo("CONNECTSTO")==0){
					int localPort=Integer.parseInt(tokens[2]);
					String otherEnd=tokens[3];
					if (tokens.length>4) otherEnd=otherEnd+tokens[4];
					otherEnds.put(localPort,otherEnd);
				}
				/* else if(paramName.compareTo("ERRORRATE")==0){
					ERROR_RATE=Integer.parseInt(tokens[2]);
				}
				else if(paramName.compareTo("DROPPINGRATE")==0){
					SWITCH_PACKET_MISSING_RATE=Integer.parseInt(tokens[2]);
				}	 */					
			}
		}
	}	
	//-----------------------------------------------------------------	
	public SimSwitch(String deviceId){
		try{loadParameters(deviceId);}catch(Exception e){}
		swMemory=new Buffer<ByteArray>("Switch Memory", memSize);		
		mactable=new MacTable(macTableSize);	
				
		portBuffer=(Buffer<ByteArray>[])new Buffer[portCount+1];
		port=new SimPort[portCount+1];
		
		for (int i=1; i<=portCount; i++){
			portBuffer[i]=new Buffer<ByteArray>("Port Buffer for Port="+"i",1);			
			port[i]=new SimPort(deviceId, Integer.toString(i), (String)otherEnds.get(i), swMemory, portBuffer[i]); //each port take instance of the switch and its id			
		}		
		start();
	}
	//=======================================================
	public int getPortCount(){return portCount;}
	/* public double getDropRate(){return SWITCH_PACKET_MISSING_RATE;}
	public double getErrorRate(){return SWITCH_PACKET_MISSING_RATE;} */	
	
	//=======================================================
	public void run(){ //works as the switching engine
		try{			
			while(true){
				ByteArray pd=getPortData();				
				int inPort=pd.getByteVal(0); 
				Frame f=new Frame(pd.getAt(1, pd.getSize()-1));		
				
				/* //System.out.println("Sending frame: "+f.getString()+"\n");	
				//Drop Packet Randomly
				double dpp = Math.random();
				if (dpp < getDropRate()) {
					System.out.println("Dropping Frame:  "+f.getString()+"\n");
					continue;
				}
				//add random error before transmitting
				double fep = Math.random();
				if (fep < getErrorRate()) {						
					System.out.println("Introducing Error to Frame:  "+f.getString()+"\n");
					addError(f);
				}
				 */
				 
				int srcMac=f.getSrcMac();
				if(!inMacTable(srcMac)){
					insertMacTable(srcMac, inPort);
					showMacTable();
				}
				int dstMac=f.getDstMac();
				if(inMacTable(dstMac)){
						int outPort=getOutPort(dstMac);						
						sendFrameToPort(f, outPort);
				}					
				else broadcastFrame(f, inPort); //default broadcast
				//System.out.println("\n");								
			}			
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
	//=======================================================
	void insertMacTable(int srcMac, int pid){
		mactable.put(srcMac,pid);
	}
	//=======================================================
	boolean inMacTable(int mac){
		int pid=mactable.get(mac);
		//System.out.println("Got PID "+ pid+ "for MAC "+mac);
		if (pid>0) return true; //need to check if search failed
		else return false;
	}
	//=======================================================
	int getOutPort(int mac){
		return mactable.get(mac);		
	}
	//=======================================================
	void showMacTable(){
		mactable.show();
	}	
	//=======================================================
	ByteArray getPortData(){
		try{
				synchronized(swMemory){
					if (swMemory.empty()) swMemory.wait();	
					ByteArray pd=swMemory.get();
					swMemory.notifyAll();					
					return pd;
				}											
		}
		catch(Exception e){
			e.printStackTrace();
		}		
		return null;
	}
	//=======================================================
	void broadcastFrame(Frame f, int inPort){
		try{
			for(int i=1; i<=portCount;i++){
				if(i!=inPort && port[i].connected()) sendFrameToPort(f,i);
			}
		}
		catch(Exception e){e.printStackTrace();}
	}
	//=======================================================
	void sendFrameToPort(Frame f, int outPort){
		try{
				synchronized(portBuffer[outPort]){
					if (portBuffer[outPort].full()) return;	//frame dropped
					else portBuffer[outPort].store(new ByteArray(f.getBytes()));
					portBuffer[outPort].notify();					
				}											
		}
		catch(Exception e){
			e.printStackTrace();
		}		
	}		
	/* //=======================================================
	private void addError(Frame f){
        byte[] b=f.getBytes();
		b[0]=-1;
    } */
	//=======================================================
	public static void main(String args[]) throws Exception{	
		
		String deviceId="Default";
		
		int argCount=args.length;		
		if (argCount>0)deviceId=args[0];
		
		SimSwitch simswitch=new SimSwitch(deviceId);			
		simswitch.join();
	}		
	//=======================================================		
}