import java.util.*;
import java.net.*;
import java.io.*;

//+++++++++++++++++Class: Next Hop+++++++++++++++++++++++++
class NextHop{
	IpAddress ip;
	int interfaceId;
	public NextHop(IpAddress i, int id){ ip=i;interfaceId=id;}
	IpAddress getIp(){return ip;}
	int getInterfaceId(){return interfaceId;}
}
//+++++++++++++++++Class: Interface Config+++++++++++++++++++++++++
class InterfaceConfiguration{
	//String interfaceId;
	IpAddress ipAddr;
	int subnetMask;
	byte macAddress;
	
	String otherEnd;
	
	boolean isConfigured=false;
	
	public InterfaceConfiguration(){}
	
	//void setInterfaceId(String id){interfaceId=id;}
	//String getInterfaceId(){return interfaceId;}
	
	void setIpAddress(IpAddress ip){ipAddr=ip;}
	IpAddress getIpAddress(){return ipAddr;}
	
	void setMacAddress(byte mac){macAddress=mac;}
	byte getMacAddress(){return macAddress;}
	
	void setSubnetMask(int mask){subnetMask=mask;}
	int getSubnetMask(){return subnetMask;}
	
	
	void setOtherEnd(String id){otherEnd=id;}
	String getOtherEnd(){return otherEnd;}
	
	void setIsConfigured(boolean b){isConfigured=b;}
	boolean getIsConfigured(){return isConfigured;}
}
//+++++++++++++++++Class: SimInterface++++++++++++++++++++++++++++++++++++
class SimInterface{
	
	String deviceId;
	int interfaceId;
	
	InterfaceConfiguration intConfig;
	
	Buffer<ByteArray> rMemory;	
	Buffer<ByteArray> interfaceBuffer;

	DataLinkLayer dll;
	boolean isUp=true;
	
	//=======================================================		
	public SimInterface(String dId, int iId, InterfaceConfiguration ifcfg, Buffer<ByteArray> mem, Buffer<ByteArray> pbuf){				
		deviceId=dId;
		interfaceId=iId;
		
		intConfig=ifcfg;	
		
		rMemory=mem;
		interfaceBuffer=pbuf;
		
		
		//System.out.println("Passing to DLL: "+ intConfig.getMacAddress());
		dll=new DataLinkLayer(deviceId, Integer.toString(interfaceId),intConfig.getOtherEnd(),intConfig.getMacAddress(), mem, pbuf);
	}
	public boolean getPortStatus(){return isUp;}
	public void setPortStatus(boolean s){
		isUp=s;
		dll.setPortStatus(s);		
	}
}
//+++++++++++++++++Class: SimRouter+++++++++++++++++++++++++++++++++
class SimRouter extends Thread{
	public int interfaceCount;	
	
	public SimInterface interfaces[];
	public InterfaceConfiguration intConfigs[];	
	Buffer<ByteArray>interfaceBuffers[];
	
	Buffer<ByteArray> rMemory;	
	Buffer<ByteArray>routeUpdate;
	
	Hashtable arpTable;	
	RoutingProtocol rProto;
	
	//--------------Constants---------------------------
	public static int MEMORY_SIZE=100;		
	public String RP_MULTICAST_ADDRESS="224.0.0.0";
	
	//----------------------------------------------------------
	public SimRouter(String deviceId){					
		arpTable=new Hashtable();
		try{
			loadParameters(deviceId);
			loadArpTable();
		}catch(Exception e){e.printStackTrace();}		
				
		rMemory=new Buffer<ByteArray>("Router Memory",MEMORY_SIZE);	
		
		interfaces=new SimInterface[interfaceCount+1];		
		interfaceBuffers=(Buffer<ByteArray>[])new Buffer[interfaceCount+1]; //assuming port count is already loaded
		for (int i=1; i<=interfaceCount; i++){
			interfaceBuffers[i]=new Buffer<ByteArray>("Port Buffer for Port="+"i",1);			
			interfaces[i]=new SimInterface(deviceId, i, intConfigs[i], rMemory, interfaceBuffers[i]); //each port take instance of the switch and its id			
		}				
		//routeUpdate=new Buffer<ByteArray>("Route Update Buffer",1);
		//new RoutingProtocol(this, rTable, routeUpdate);
		rProto=new RoutingProtocol(this);
		new ConsoleInput(this);
		start();		
	}
	//----------------------------------------------------------
	public void loadParameters(String deviceId) throws Exception{		
		BufferedReader br=new BufferedReader(new FileReader("..//Config//Config.txt"));	
		//System.out.println("Loading Parameters.");
		String line;
		while((line=br.readLine())!=null){
			String[] tokens= line.split(":");			
			if(tokens[0].compareTo(deviceId)==0){
				//System.out.println(line);
				String paramName=tokens[1];
				if(paramName.compareTo("NUMOFPORTS")==0){
					interfaceCount=Integer.parseInt(tokens[2]);
					intConfigs=new InterfaceConfiguration[interfaceCount+1]; //indexing starts from 1
					for(int i=1;i<=interfaceCount;i++) intConfigs[i]=new InterfaceConfiguration();
				}
				else if(paramName.compareTo("CONNECTSTO")==0){
					int interfaceId=Integer.parseInt(tokens[2]);
					String otherEnd=tokens[3];
					if (tokens.length>4) otherEnd=otherEnd+tokens[4];
					//otherEnds.put(localPort,otherEnd);
					intConfigs[interfaceId].setOtherEnd(otherEnd);
				}
				else if(paramName.compareTo("PORTIPMASK")==0){
					int interfaceId=Integer.parseInt(tokens[2]);					
					intConfigs[interfaceId].setIpAddress(new IpAddress(tokens[3]));
					intConfigs[interfaceId].setSubnetMask(Integer.parseInt(tokens[4]));					
					
					intConfigs[interfaceId].setIsConfigured(true);										
				}
				else if(paramName.compareTo("PORTMAC")==0){
					int interfaceId=Integer.parseInt(tokens[2]);					
					intConfigs[interfaceId].setMacAddress((byte)Integer.parseInt(tokens[3]));	
					//System.out.println("PORTMAC:" +tokens[3]);	
				}									
			}
		}
	}
	//----------------------------------------------------------
	public InterfaceConfiguration[] getInterfaceConfigurations(){return intConfigs;}
	public void setPortStatus(int interfaceId, boolean status){
		interfaces[interfaceId].setPortStatus(status);
		rProto.notifyPortStatusChange(interfaceId, status);
	}		
	//----------------------------------------------------------
	public void loadArpTable() throws Exception{			
		BufferedReader br=new BufferedReader(new FileReader("..//Config//ArpTable.txt"));		
		String line;
		while((line=br.readLine())!=null){
			String[] tokens= line.split(":");
			IpAddress dst=new IpAddress(tokens[0]);
			for(int i=1;i<=interfaceCount;i++){
				if(intConfigs[i].getIpAddress()!=null){
					if(dst.sameSubnet(intConfigs[i].getIpAddress(), intConfigs[i].getSubnetMask())){
						arpTable.put(tokens[0],tokens[1]);
						break;
					}
				}
			}
		}
	}
	//----------------------------------------------------------
	int getMacFromArpTable(IpAddress ip){
		return Integer.parseInt((String)arpTable.get(ip.getString()));
	}	
	//----------------------------------------------------------
	public ByteArray receiveFromDll(){
		try{
				synchronized(rMemory){
					if (rMemory.empty()) rMemory.wait();	
					ByteArray p=rMemory.get();
					rMemory.notify();
					return p;	
				}											
		}
		catch(Exception e){
			e.printStackTrace();
		}
		return null;
	}
	//----------------------------------------------------------
	void sendPacketToInterface(ByteArray p, int outInterface){
		try{
				if(interfaces[outInterface].getPortStatus()==true){
					synchronized(interfaceBuffers[outInterface]){
						if (interfaceBuffers[outInterface].full()) return;	//frame dropped
						else interfaceBuffers[outInterface].store(new ByteArray(p.getBytes()));
						interfaceBuffers[outInterface].notify();					
					}
				}											
		}
		catch(Exception e){
			e.printStackTrace();
		}		
	}	
	//----------------------------------------------------------
	public void run(){
		try{
			while(true){
				ByteArray b=receiveFromDll();							
				
				int interfaceId=b.getByteVal(0);				
				
				Packet p=new Packet(b.getAt(1,b.getSize()-1));
				IpAddress dst=p.getDstIp();	
				
				int dstMac;
				ByteArray temp;	
				
				//Check if packet is from an active port
				if(interfaces[interfaceId].getPortStatus()==true){
					//Now handle packet
					
					//1. Check if it is a route update packet
					if (dst.sameIp(new IpAddress(RP_MULTICAST_ADDRESS))){
							//rProto.notifyRouteUpdate(new ByteArray(p.getBytes()));
							rProto.notifyRouteUpdate(b);
					}
					
					//2. else if it is data packet, then route
					else{
						System.out.println("Received Packet: "+p.getString());
						//NextHop nextHop=rTable.getNextHop(dst);
						NextHop nextHop=rProto.getNextHop(dst);
						
						//2.1 If No route available, then send destination unreachable to sender
						if (nextHop==null){//unreachable
							System.out.println("Destination Unreachable");
							String text="Destination Unreachable";						
							Packet rep=new Packet(intConfigs[interfaceId].getIpAddress(), p.getSrcIp(), text.getBytes());
						
							nextHop=rProto.getNextHop(p.getSrcIp());
							
							dstMac=getMacFromArpTable(nextHop.getIp());
							temp=new ByteArray((rep.getBytes()).length+1);
							temp.setByteVal(0,(byte)dstMac);
							temp.setAt(1,rep.getBytes());
							
							sendPacketToInterface(temp, nextHop.getInterfaceId());
							
						}
						
						//2.2 A route found for the packet					
						else{
							//2.2.1 check if packet is for router itself
							if((intConfigs[nextHop.getInterfaceId()].getIpAddress()).sameIp(nextHop.getIp()))
								System.out.println("\tShowing Packet: "+p.getString());
								
							//2.2.2 else forward packet to next hop	
							else{						
								System.out.println("Routing Packet: "+p.getString());
								dstMac=getMacFromArpTable(nextHop.getIp());
								temp=new ByteArray((p.getBytes()).length+1);
								temp.setByteVal(0,(byte)dstMac);
								temp.setAt(1,p.getBytes());
								
								sendPacketToInterface(temp, nextHop.getInterfaceId());
							}
						}
					}						
				}	
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	//----------------------------------------------------------
	public static void main(String args[]) throws Exception{	
		String deviceId="Default";
		
		int argCount=args.length;		
		if (argCount>0)deviceId=args[0];
		
		SimRouter simrouter=new SimRouter(deviceId);		
		simrouter.join();
	}		
}

//++++++++++++++++++Class: ConsoleInput++++++++++++++++++++++++++++++++++++++++++	
class ConsoleInput extends Thread{	
	SimRouter sRouter;	
	public ConsoleInput(SimRouter s){
		sRouter=s;
		start();
	}
	public void run(){
		String userInput;
		try{
			BufferedReader inFromUser= new BufferedReader(new InputStreamReader(System.in));
			while (true){		
				userInput=inFromUser.readLine();
				//System.out.println("Input "+userInput);
				
				int colon=userInput.indexOf(':');
				String command=userInput.substring(0,colon);
				
				if(command.compareTo("Down")==0){					
					int interfaceId=Integer.parseInt(userInput.substring(colon+1));					
				    sRouter.setPortStatus(interfaceId,false);
				}
				if(command.compareTo("Up")==0){										
					int interfaceId=Integer.parseInt(userInput.substring(colon+1));										
				    sRouter.setPortStatus(interfaceId,true);
				}				
			}		
		}catch(Exception e){}		
	}
}
