import java.util.*;
import java.net.*;
import java.io.*;
//+++++++++++++++++Class: SimPhy+++++++++++++++++++++++++++++++++
class SimPhy{	
	Socket sock;	
	DataInputStream br;
	OutputStream bw;
	
	boolean hasConnection=false;
	
	Buffer<ByteArray> phy2dll;
	Buffer<ByteArray> dll2phy;
	
	String portId="";
	
	boolean isUp=true;
	//-----------------------------------------------------------------		
	SimPhy(String deviceId, String pId, String otherEnd, Buffer<ByteArray> p2d, Buffer<ByteArray> d2p){		
		try{
			portId=pId;
			//System.out.println("End "+otherEnd);
			sock = new Socket("127.0.0.1",9009);
			//System.out.println("Debug: Physical Layer Connected"+"\n"); 
			hasConnection=true;
	
			br=new DataInputStream(sock.getInputStream());			
				
			bw=sock.getOutputStream();	
				
			String temp=deviceId+portId;//=deviceId+portId+otherEnd+"\n";
			if(otherEnd!=null) temp=temp+":"+otherEnd;				
			
			SimPhy.writeStuffed(bw,temp.getBytes());			
				
			phy2dll=p2d;
			dll2phy=d2p;
			new PhySend(this);
			new PhyReceive(this);
		}
		catch(Exception e){
			e.printStackTrace();
		}		
	}
	//----------------------------------------------------------------------------------
	public void setPortStatus(boolean s){
			isUp=s;
			System.out.println("Port Status updated for "+portId+" to "+s);
	}
	public boolean getPortStatus(){return isUp;}
	//-------------------------------------------------------------------------------------------
	public static void writeStuffed(OutputStream bw, byte[] f){						
		try{			
			byte[] temp=SimPhy.bitStuff(f);			
			ByteArray b=new ByteArray(temp.length+2);
			b.setByteVal(0,(byte)126);//here there is only post amble. Preamble may be added
			b.setAt(1,temp);
			b.setByteVal(temp.length+1,(byte)126);//here there is only post amble. Preamble may be added			
			bw.write(b.getBytes());
		}catch(Exception e){}
	}	
	//-------------------------------------------------------------------------------------------	
	public static byte[] readDeStuffed(DataInputStream br){					
		byte[] b=new byte[1000]; //this size is arbitrary.
		int count=0;		
		try{
			byte i=br.readByte();			
						
			while(i!=126){
				i=br.readByte();
			}//skip as long as there is no preamble
			
			i=br.readByte();			
			while(i!=126){				
				b[count++]=i;
				//add code incase count>size of b.				
				i=br.readByte();
			}
			byte[] temp=new byte[count];		
			System.arraycopy(b,0,temp,0,count);
			
			/*demonstrate the effect of bit stuff*/
			return SimPhy.bitDeStuff(temp);
			//return temp;
		}
		catch(Exception e){
			e.printStackTrace();
		}				
		return null;
	}
	//=======================================================	
	public static byte[] bitStuff(byte[] b){		
		String total = "";
		for (int i = 0; i < b.length; i++)
           {
			String bitString = String.format("%8s", Integer.toBinaryString(b[i] & 0xFF)).replace(' ', '0');
			total += bitString;
		}
		//System.out.println(total);
		total = CRC_encode(total);
		while (total.length()%8!=0)
		{
			total += "0";
		}
		//System.out.println(total);
		total = total.replaceAll("11111", "111110");
		while (total.length()%8!=0)
		{
			total += "0";
		}
		int length = total.length()/8;
		byte [] res = new byte[length];
		int idx = 0;
		for (int i = 0; i < total.length(); i+=8)
           {
			String bitString = total.substring(i,i+8);
			//total += bitString;
			res[idx++] = (byte) Integer.parseInt(bitString, 2);
		}
		//System.out.println(total);
		return res;	
	}
	//-----------------------------------------------------------------	
	public static byte[] bitDeStuff(byte[] b){
		String total = "";
		for (int i = 0; i < b.length; i++)
           {
			String bitString = String.format("%8s", Integer.toBinaryString(b[i] & 0xFF)).replace(' ', '0');
			total += bitString;
		}
		int cut_length = 8-((count(total,"111110")+0) % 8);
		total = total.replaceAll("111110", "11111");
		if (total.length()%8!=0)
		{
			total = total.substring(0,total.length()-cut_length);
		}
		//System.out.println(total);
		total = CRC_decode(total.substring(0,total.length()-5));
		//System.out.println(total);
		if (total == null) {
			System.out.println("CRC Check failed");
			//return null;
		}
		else System.out.println("CRC Check passed.");
		byte [] res = new byte[total.length()/8];
		int idx = 0;
		for (int i = 0; i < total.length(); i+=8)
           {
			String bitString = total.substring(i,i+8);
			//total += bitString;
			res[idx++] = (byte) Integer.parseInt(bitString, 2);
		}
		//System.out.println(total);
		return res;	
	
	}
	//-----------------------------------------------------------------
	public static int count(String str, String target) {
    		return (str.length() - str.replace(target, "").length()) / target.length();
	}

	public static String stringXOR(String input, String divisor)
    {
		String res = "";
		for (int i = 0; i < input.length(); i++ )
		{
			if (input.charAt(i)!=divisor.charAt(i)) res += "1";
			else res+="0";
		}	
		return res;
	}

	public static String CRC_encode(String input)
	{
		//polynomial x^3 + x + 1
		String divisor = "1011";
		String output = input + "000";
		String reference = input.replaceAll("1","0");
		for (int i = 0; i < output.length()-3; )
		{
			while (output.charAt(i)=='0') {
				i++;
			}
			String temp = stringXOR(output.substring(i,i+4),divisor);
			StringBuffer buf = new StringBuffer(output);
			int start = i;
			int end = i+4;
			buf.replace(start, end, temp);
      		//System.out.println(buf.toString());
			output = buf.toString();
			if (output.substring(0,output.length()-3).equals(reference)) break;
      
      
		} 
    	return input + output.substring(output.length()-3, output.length());
	}

	public static String CRC_decode(String input)
	{
		//polynomial x^3 + x + 1
		String divisor = "1011";
		String output = input;
		String reference = output.replaceAll("1","0");
		for (int i = 0; i < output.length()-3;)
		{
			while (output.charAt(i)=='0') {
				i++;
			}
			String temp = stringXOR(output.substring(i,i+4), divisor);
			StringBuffer buf = new StringBuffer(output);
			int start = i;
			int end = i+4;
			buf.replace(start, end, temp);
			output = buf.toString();
      		//System.out.println(buf.toString());
			if (output.equals(reference)) return input.substring(0,input.length()-3);
		}
    	return null;
  	}

	public boolean connected(){return hasConnection;}
	//-----------------------------------------------------------------	
	public Frame receiveFromDll(){
		try{
				synchronized(dll2phy){
					if (dll2phy.empty()) dll2phy.wait();	
					Frame f=new Frame((dll2phy.get()).getBytes());
					dll2phy.notify();
					return f;	
				}											
		}
		catch(Exception e){
			e.printStackTrace();
		}
		return null;
	}
	//-----------------------------------------------------------------	
	public void sendToLine(Frame f){
		try{
			//if ((hasConnection) && (isUp)){						
				SimPhy.writeStuffed(bw,f.getBytes());					
				if(portId.compareTo("")==0) System.out.println("Frame Sent: "+f.getString()+"\n");
				else System.out.println("Sent Frame Through Port= "+portId+" : "+f.getString()+"\n");
			//}			
		}
		catch (Exception e){
			e.printStackTrace();
		}
	}
	//-----------------------------------------------------------------	
	public void sendToDll(Frame f){
		try{
				synchronized(phy2dll){
					if (phy2dll.full()) phy2dll.wait();
					ByteArray b;
					if(portId.compareTo("")==0){b=new ByteArray(f.getBytes());}
					else{
						b=new ByteArray((f.getBytes()).length+1);
						b.setByteVal(0,(byte)Integer.parseInt(portId));
						b.setAt(1,f.getBytes());
					}
					phy2dll.store(b);
					phy2dll.notify();
				}
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
	//-----------------------------------------------------------------	
	public Frame receiveFromLine(){	
		try{
			//if (hasConnection && isUp){	//isUp should be inside synchronized block									
				byte[] temp=readDeStuffed(br);
				Frame f=new Frame(temp);
				if(getPortStatus()){
					if(portId.compareTo("")==0)System.out.println("\tFrame Received: "+f.getString()+"\n");
					else System.out.println("\tReceived Frame on Port= "+portId+" : "+f.getString()+"\n");	
					return f;
				}
			//}
		}
		catch(Exception e){
			e.printStackTrace();
		}
		return null;		
	}	
	//=======================================================
	private class PhySend extends Thread{
		SimPhy simphy;		
		public PhySend(SimPhy s){
			simphy=s;			
			start();
		}
		public void run(){			
			while (true){
				//if(simphy.getPortStatus()){
					Frame f=simphy.receiveFromDll();
					simphy.sendToLine(f);
				//}
			}
		}
	}
	//=======================================================
	private class PhyReceive extends Thread{
		SimPhy simphy;		
		public PhyReceive(SimPhy s){
			simphy=s;			
			start();
		}
		public void run(){
			while(true){
				//if(simphy.portId!=""){
					//System.out.println("Interface "+portId+" is"+simphy.isUp);
				//}
				//if(simphy.getPortStatus()){
					Frame f=simphy.receiveFromLine();
					if(simphy.getPortStatus()){
						if (f!=null) simphy.sendToDll(f);
					}
			}
		}
	}		
//=======================================================
}
