import java.util.*;
import java.util.Map.Entry;
import java.net.*;
import java.io.*;

//+++++++++++++++++Class: MyTimer++++++++++++++++++++++++++++++++++++
class MyTimer extends Thread{
		RoutingProtocol rP;		
		boolean running;		
		int duration;
		IpAddress ipAddress;

		int type; //Two types: invalidate timer and update timer
		
		public MyTimer(RoutingProtocol r, int t){			
			rP=r;			
			running=false;
			type=t;
		}
        public void startTimer(int timeout_duration) {
            running=true;			
			duration=timeout_duration*1000;
			start();
			//System.out.println("Scheduling Timer: "+timerId+"\n");			
        }
		public void stopTimer() {          
            //try{timer.cancel();}catch(Exception e){e.printStackTrace();} 
			running=false;
			this.interrupt();
			//System.out.println("Stopping Timer: "+timerId+"\n");
        }
		public void setIpAddress(IpAddress ipAddress)
		{
			this.ipAddress = ipAddress;
		}
		public IpAddress getIpAddress()
		{
			return this.ipAddress;
		}
		public boolean isRunning(){
			return running;
		}	
		public void run() {			
			try{
				//Start Timer
				Thread.sleep(duration);  								
				//Timer Expired
				running=false;				
				rP.handleTimerEvent(type);
			}
			catch (InterruptedException e){
					e.printStackTrace();
			}
        }			
}

class RoutingTableEntry
{
	private int hopCount;
	private NextHop nextHop;
	public boolean invalidate;
	//private int interfaceId;

	public RoutingTableEntry()
	{
		this.hopCount = 0;
		this.nextHop = null;
		this.invalidate = true;
		//this.interfaceId = 0;
	}

	public RoutingTableEntry(int hopCount, NextHop nextHop)
	{
		this.hopCount = hopCount;
		this.nextHop = nextHop;
		this.invalidate = true;
		//this.interfaceId = interfaceId;
	}


	public void setHopCount(int hopCount)
	{
		this.hopCount = hopCount;
	}

	public int getHopCount()
	{
		return this.hopCount;
	}

	public void setNextHop(NextHop nextHop)
	{
		this.nextHop = nextHop;
	}

	public NextHop getNextHop()
	{
		return this.nextHop;
	}

	//public void setInterfaceId(int interfaceId)
	//{
	//	this.interfaceId = interfaceId;
	//}

	//public int getInterfaceId()
	//{
	//	return this.interfaceId;
	//}
}

//+++++++++++++++++Class: RoutingProtocol+++++++++++++++++++++++++++++++++
class RoutingProtocol extends Thread{	
	SimRouter simrouter;
	Buffer<ByteArray> updateBuffer;

	public static int UPDATE_TIMER_TYPE = 0;
	public static int UPDATE_TIMER_VALUE=30;
	public static int INVALID_TIMER_VALUE=90;
    public static String RP_MULTICAST_ADDRESS="224.0.0.0";

	private HashMap<String, RoutingTableEntry> routingTable;
	//To Do: Declare any other variables required
	//----------------------------------------------------------------------------------------------	
	public RoutingProtocol(SimRouter s){
		simrouter=s;		
		routingTable = new HashMap<String, RoutingTableEntry>();
		updateBuffer = new Buffer<ByteArray>("updateBuffer", 128);	
		start();
	}
	//------------------------Routing Function-----------------------------------------------	
	void notifyRouteUpdate(ByteArray p){//invoked by SimRouter
		//Stores the update data in a shared memory to be processed by the 'RoutingProtocol' thread later.
		try{
				synchronized(updateBuffer){
					if (updateBuffer.full()) return;	
					else updateBuffer.store(p);
					updateBuffer.notify();					
				}											
		}
		catch(Exception e){
			e.printStackTrace();
		}		
	}
	//----------------------------------------------------------------------------------------------	
	public void notifyPortStatusChange(int interfaceId, boolean status){//invoked by SimRouter
		//To Do: Update the routing table according to the changed status of an interface. If interface in UP (status=TRUE), add to routing table, if interface is down, remove from routing table
		if (status)
			routingTable.put(this.simrouter.intConfigs[interfaceId].getIpAddress().getNetworkAddress(this.simrouter.intConfigs[interfaceId].getSubnetMask()).getString(), new RoutingTableEntry(0,new NextHop(this.simrouter.intConfigs[interfaceId].getIpAddress(),interfaceId)));
		else routingTable.remove(this.simrouter.intConfigs[interfaceId].getIpAddress().getNetworkAddress(this.simrouter.intConfigs[interfaceId].getSubnetMask()).getString());
	}
	//---------------------Forwarding Function------------------------------------------
	NextHop getNextHop(IpAddress dstIp){//invoked by SimRouter
		//To Do: Write code that  returns an NextHop object corresponding the destination IP Address, dstIP. If route in unknown, return null
		//dstIp.
		if (routingTable.get(dstIp.getString())!=null)
			return routingTable.get(dstIp.getString()).getNextHop();
		for (Entry e : routingTable.entrySet())
		{
			IpAddress networkIp = new IpAddress((String) e.getKey());
			NextHop nextHop = ((RoutingTableEntry) e.getValue()).getNextHop();
			int intfId = nextHop.getInterfaceId();
			String networkAddress = dstIp.getNetworkAddress(simrouter.intConfigs[intfId].getSubnetMask()).getString();
			if (routingTable.get(networkAddress)!=null)
			{
				//System.out.println("Gotcha");
				return routingTable.get(networkAddress).getNextHop();
			}
		}
		return null; //default return value
	}	
	//-------------------Routing Protocol Thread--------------------------------------	
	public void run(){
		//To Do 1: Populate Routing Table with directly connected interfaces using the SimRouter instance. Also print this initial routing table	.	
		System.out.println("PRINTING INITIAL ROUTING TABLE");
		for (SimInterface intf : simrouter.interfaces)
		{
			if (intf!=null && intf.getPortStatus() && intf.intConfig.getIsConfigured()) 
			{
				//System.out.println(intf.intConfig.getIpAddress().getString());
				//System.out.println(intf.intConfig.getIpAddress().getString());
				//String strIp = intf.intConfig.getIpAddress().getNetworkAddress(intf.intConfig.getSubnetMask()).getString();
				String strIp = intf.intConfig.getIpAddress().getString();
				RoutingTableEntry rte = new RoutingTableEntry(0,new NextHop(intf.intConfig.getIpAddress(), intf.interfaceId));
				//if (rte == null) System.out.println("rte is null");
				//if (intf.intConfig.getIpAddress().getString()==null) System.out.println("ip is null");
				//System.out.println(intf.intConfig.getIpAddress().getString());
				routingTable.put(strIp, rte);
				for (Object k : simrouter.arpTable.keySet())
				{
					String strIpEx = (String) k;
					if (routingTable.get(strIpEx)==null && new IpAddress(strIpEx).sameSubnet(new IpAddress(strIp),intf.intConfig.getSubnetMask()))
					{
						RoutingTableEntry rteEx = new RoutingTableEntry(1,new NextHop(new IpAddress(strIpEx), intf.interfaceId));
						routingTable.put(strIpEx, rteEx);
					}
				}
				//strIp = intf.intConfig.getIpAddress().getNetworkAddress(intf.intConfig.getSubnetMask()).getString();
				//rte = new RoutingTableEntry(1,new NextHop(new IpAddress(strIp), intf.interfaceId));
				//routingTable.put(strIp, rte);
			}
		}
		

		//System.out.println("boo");
		for(Entry e : routingTable.entrySet()){
			System.out.print("Destination: " + ((String) e.getKey()) + ", Number of Hops: " + ((RoutingTableEntry) e.getValue()).getHopCount());
		    if (((RoutingTableEntry) e.getValue()).getNextHop()!=null) System.out.println(", Next Hop: " + ((RoutingTableEntry) e.getValue()).getNextHop().getIp().getString());
			else System.out.println("");
		}
		
		//To Do 2: Send consructed Routing table immediately to all the neighbors. Start the update timer.
		String text = "";
		IpAddress dstIp = new IpAddress("0.0.0.0");
		for(Entry e : routingTable.entrySet()){
			RoutingTableEntry rte = (RoutingTableEntry) e.getValue();
			//dstIp = (String) e.getKey();
			text +=(String) e.getKey();
			text += ":";
			text += rte.getHopCount();
			text += ";";
		}
		if (!text.equals("")) text = text.substring(0, text.length()-1);

		Packet rep;
			
		
		for (SimInterface i : simrouter.interfaces)
			if (i!=null && i.getPortStatus() && i.intConfig.getIsConfigured()) 
			{
				for (Object ipKey: simrouter.arpTable.keySet())
				{
					IpAddress ip = new IpAddress((String) ipKey);
					if (ip!=null && ip.sameSubnet(i.intConfig.getIpAddress(), i.intConfig.subnetMask))
					{
						if (i.intConfig.getMacAddress() == (byte) simrouter.getMacFromArpTable(ip)) continue;
						rep =new Packet(i.intConfig.getIpAddress() , new IpAddress(RP_MULTICAST_ADDRESS),  text.getBytes());
						ByteArray temp=new ByteArray((rep.getBytes()).length+1);
						temp.setByteVal(0,(byte) simrouter.getMacFromArpTable(ip));
						temp.setAt(1,rep.getBytes());
						simrouter.sendPacketToInterface(temp, i.interfaceId);
					}
				}
			}

		new MyTimer(this, UPDATE_TIMER_TYPE).startTimer(UPDATE_TIMER_VALUE);
		//Continuously check whether there are routing updates received from other neighbors.
		ByteArray p;
		try{
			while(true){
				synchronized(updateBuffer){
					if (updateBuffer.empty()) updateBuffer.wait();	
					p=updateBuffer.get();
					updateBuffer.notify();				
				}
				
				int interfaceId=p.getByteVal(0);				
				
				Packet pac=new Packet(p.getAt(1,p.getSize()-1));
				IpAddress src=pac.getSrcIp();	
				
				//int dstMac;
				//ByteArray temp;	

				//Packet pac = new Packet(p.getAt(0,p.getSize()));
				String payload = new String(pac.getPayload());
				String [] routingUpdates = payload.split(";");
				
				//An update has been received, Now:
				//To Do 3.1: Modify routing table according to the update received. 
				//To Do 3.2: Start invalidate timer for each newly added/updated route if any.

				boolean changed = false;
				for (String update : routingUpdates)
				{
					String [] tmp = update.split(":");
					if (routingTable.get(tmp[0])!=null)
					{
						if ((Integer.parseInt(tmp[1]) + 1) < routingTable.get(tmp[0]).getHopCount()) {
							changed = true;
							routingTable.remove(tmp[0]);
							routingTable.put(new IpAddress(tmp[0]).getString(), new RoutingTableEntry(Integer.parseInt(tmp[1])+1, new NextHop(src, interfaceId)));
							if (routingTable.get(tmp[0]).invalidate == true)
							{
								routingTable.get(tmp[0]).invalidate = false;
								int result = 0;  
								for (byte b: new IpAddress(tmp[0]).getBytes())  
								{  
									result = result << 8 | (b & 0xFF);  
								}
								new MyTimer(this, result).start();
							} 
						}
					}
					else {
						changed = true;
						routingTable.put(new IpAddress(tmp[0]).getString(), new RoutingTableEntry(Integer.parseInt(tmp[1])+1, new NextHop(src, interfaceId)));
						
					}
				}

				
				//To Do 3.3:Print the routing table if the routing table has changed
				if (changed)
				{
					System.out.println("PRINTING UPDATED ROUTING TABLE");
					for(Entry e : routingTable.entrySet()){
						System.out.print("Destination: " + ((String) e.getKey()) + ", Number of Hops: " + ((RoutingTableEntry) e.getValue()).getHopCount());
						if (((RoutingTableEntry) e.getValue()).getNextHop()!=null) System.out.println(", Next Hop: " + ((RoutingTableEntry) e.getValue()).getNextHop().getIp().getString());
						else System.out.println("");
					}
				}
				//To Do Optional 1: Send Triggered update to all the neighbors and reset update timer.
			}
		}
		catch(Exception e){
			e.printStackTrace();
		}		
	}	
	//----------------------Timer Handler---------------------------------------------------------------------	
	public void handleTimerEvent(int type){
		if (type == UPDATE_TIMER_TYPE)
		{	
			String text = "";
			IpAddress dstIp = new IpAddress("0.0.0.0");
			for(Entry e : routingTable.entrySet()){
				RoutingTableEntry rte = (RoutingTableEntry) e.getValue();
				//dstIp = (IpAddress) e.getKey();
				text += e.getKey();
				text += ":";
				text += rte.getHopCount();
				//text += ":";
				//text += rte.getNextHop().getInterfaceId();
				text += ";";
			}
			text = text.substring(0, text.length()-1);

			Packet rep;
			
		
			for (SimInterface i : simrouter.interfaces)
				if (i!=null && i.getPortStatus() && i.intConfig.getIsConfigured()) 
				{
					for (Object ipKey: simrouter.arpTable.keySet())
					{
						IpAddress ip = new IpAddress((String) ipKey);
						if (ip.sameSubnet(i.intConfig.getIpAddress(), i.intConfig.subnetMask))
						{
							if (i.intConfig.getMacAddress() == (byte) simrouter.getMacFromArpTable(ip)) continue;
							rep =new Packet(i.intConfig.getIpAddress() , new IpAddress(RP_MULTICAST_ADDRESS),  text.getBytes());
							ByteArray temp=new ByteArray((rep.getBytes()).length+1);
							temp.setByteVal(0,(byte) simrouter.getMacFromArpTable(ip));
							temp.setAt(1,rep.getBytes());
							simrouter.sendPacketToInterface(temp, i.interfaceId);
						}
					}
				}


			new MyTimer(this, UPDATE_TIMER_TYPE).startTimer(UPDATE_TIMER_VALUE);
		}	
		else {
			IpAddress tempIp = new IpAddress(new byte[] {
											(byte)(type >> 24),
											(byte)(type >> 16),
											(byte)(type >> 8),
											(byte)type});
			routingTable.remove(tempIp.getString());
		}
	}			
	//----------------------------------------------------------------------------------------------
}

