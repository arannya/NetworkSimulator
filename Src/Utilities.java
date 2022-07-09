import java.util.*;
import java.net.*;
import java.io.*;

//+++++++++++++++++Class: IPAddress++++++++++++++++++++++++++++++++++++
class IpAddress{
	byte[] ipAddr=new byte[4];
	//-----------------------------------------------------------------	
	public IpAddress(String ipString){					
		StringTokenizer strTok=new StringTokenizer(ipString,".",false);		
		int i=0;
		String octet;
		while(strTok.hasMoreTokens()){
			octet=strTok.nextToken();			
			ipAddr[i++]=(byte)Integer.valueOf(octet,10).intValue();						
		}
	}
	//-----------------------------------------------------------------	
	public IpAddress(byte[] ip){
		System.arraycopy(ip, 0, ipAddr, 0, 4);
	}
	//-----------------------------------------------------------------	
	public byte[] getBytes(){return ipAddr;}
	//-----------------------------------------------------------------	
	public String getString(){return new String((int)(ipAddr[0]& 0xFF)+"."+(int)(ipAddr[1]& 0xFF)+"."+(int)(ipAddr[2]& 0xFF)+"."+(int)(ipAddr[3]& 0xFF));}
	//-----------------------------------------------------------------	
	public IpAddress getNetworkAddress(int mask){		
		byte[] netMask=new byte[4];		
		//find mask
		int j=0;
		int tMask=128;
		for(int i=0;i<32;i++){			
			if(i<mask){
				tMask=(tMask>>1)|128;
			}
			if((i+1)%8==0){				
				netMask[j++]=(byte)tMask;
				tMask=0;
			}			
		}		
		//find network address		
		byte[] netAddr=new byte[4];
		for(int i=0;i<4;i++){						
			netAddr[i]=(byte)(ipAddr[i] & netMask[i]);
		}
		return new IpAddress(netAddr);
	}
	//-----------------------------------------------------------------	
	public boolean sameSubnet(IpAddress ip, int mask){
		IpAddress network1=getNetworkAddress(mask);
		IpAddress network2=ip.getNetworkAddress(mask);
		return network1.sameIp(network2);
	}
	//-----------------------------------------------------------------	
	public boolean sameIp(IpAddress ip){		
		byte[] other=ip.getBytes();
		for (int i=0; i<4; i++) {
			if(ipAddr[i]!=other[i]){
				return false;				
			}	
		}
		return true;
	}
	//-----------------------------------------------------------------	
	// public boolean checkValid(){
		// return true;
	// }
	//=======================================================	
}
//+++++++++++++++++Class: Packet++++++++++++++++++++++++++++++++++++
class Packet{	
	IpAddress src;
	IpAddress dst;
	byte payload[];		
	//-----------------------------------------------------------------		
	Packet(byte[] a){
		byte[] srcIp=new byte[4];
		byte[] dstIp=new byte[4];
		payload=new byte[a.length-8];
		System.arraycopy(a, 0, srcIp, 0, 4);		
		System.arraycopy(a, 4, dstIp, 0, 4);		
		System.arraycopy(a, 8, payload, 0, a.length-8);		
		src=new IpAddress(srcIp);
		dst=new IpAddress(dstIp);
	}
	//-----------------------------------------------------------------	
	Packet(IpAddress s, IpAddress d, byte[] a){		
		payload=new byte[a.length];		
		System.arraycopy(a, 0, payload, 0, a.length);		
		src=s;
		dst=d;
	}
	//-----------------------------------------------------------------	
	IpAddress getSrcIp(){return src;}
	IpAddress getDstIp(){return dst;}
	byte[] getPayload(){return payload;}
	//-----------------------------------------------------------------	
	byte [] getBytes(){
		byte[] packet=new byte[payload.length+8];		
		try{
			System.arraycopy(src.getBytes(), 0, packet, 0, 4);		
			System.arraycopy(dst.getBytes(), 0, packet, 4, 4);		
			System.arraycopy(payload, 0, packet, 8, payload.length);		
			return packet;
		}
		catch(Exception e){
			e.printStackTrace();
		}
		return null;
	}
	//-----------------------------------------------------------------	
	String getString(){				
		return new String("SrcIP="+src.getString()+ " | DestIP="+dst.getString()+" | Payload="+new String(payload));						
	}	
}

//+++++++++++++++++Class: Frame++++++++++++++++++++++++++++++++++++
class Frame{
	byte srcMac;
	byte dstMac;	
	byte payload[];	
	//-----------------------------------------------------------------	
	Frame(byte[] a){
		srcMac=a[0];
		dstMac=a[1];		
		payload=new byte[a.length-2];		
		try{
			System.arraycopy(a, 2, payload, 0, payload.length);
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
	//-----------------------------------------------------------------	
	Frame(int s, int d, byte[] a){
		srcMac=(byte)s;
		dstMac=(byte)d;				
		payload=new byte[a.length];
		
		try{
			System.arraycopy(a, 0, payload, 0, a.length);
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
	//-----------------------------------------------------------------	
	Frame(int s, int d){		
		srcMac=(byte)s;
		dstMac=(byte)d;		
		payload=new byte[0];	
	}		
	//-----------------------------------------------------------------	
	byte getSrcMac(){
		return srcMac;
	}
	byte getDstMac(){
		return dstMac;
	}			
	byte [] getPayload(){		
		return payload;
	}
	//-----------------------------------------------------------------	
	byte [] getBytes(){
		byte[] frame=new byte[payload.length+2];
		frame[0]=srcMac;
		frame[1]=dstMac;		
		try{
			System.arraycopy(payload, 0, frame, 2, payload.length);
			return frame;
		}
		catch(Exception e){
			e.printStackTrace();
		}
		return null;
	}	
	//-----------------------------------------------------------------	
	String getString(){		
		//return new String("Src Mac="+(int)(srcMac& 0xFF)+ " | Dst Mac="+(int)(dstMac& 0xFF)+" | Payload="+new String(payload));						
		return new String("Src Mac="+(int)(srcMac& 0xFF)+ " | Dst Mac="+(int)(dstMac& 0xFF));						
	}	
	//-----------------------------------------------------------------	
	boolean hasCheckSumError(){
		/*replace the following code with checksum verification*/
		if(srcMac==-1) return true;
		else return false;
	}
	//-----------------------------------------------------------------		
}

//+++++++++++++++++Class: ByteArray++++++++++++++++++++++++++++++++++++
class ByteArray{
	byte[] bArray;
	//-----------------------------------------------------------------	
	ByteArray(int size){
		bArray=new byte[size];
	}
	//-----------------------------------------------------------------	
	ByteArray(byte[] b){
		bArray=new byte[b.length];
		System.arraycopy(b, 0, bArray, 0, b.length);
	}
	//-----------------------------------------------------------------	
	void setAt(int index, byte[] b){
		System.arraycopy(b, 0, bArray, index, b.length);
	}
	//-----------------------------------------------------------------	
	byte getByteVal(int index){return bArray[index];}
	void setByteVal(int index, byte b){bArray[index]=b;}
	//-----------------------------------------------------------------	
	byte[] getAt(int index, int length){
		byte[] temp=new byte[length];
		System.arraycopy(bArray, index, temp, 0, length);
		return temp;
	}
	//-----------------------------------------------------------------	
	byte[] getBytes(){return bArray;}
	int getSize(){return bArray.length;}
}


//+++++++++++++++++Class: Buffer+++++++++++++++++++++++++++++++++
class Buffer<T>{
	T data[];	
	int size;
	int head;
	int tail;
	String name;	
	//-----------------------------------------------------------------	
	Buffer(String n, int sz){
		name=n;
		size=sz+1;
		data=(T[])new Object[size];	
		head=0;
		tail=0;
	}	
	//-----------------------------------------------------------------	
	synchronized boolean empty(){
		if (head==tail) return true;
		else return false;
	}
	//-----------------------------------------------------------------	
	synchronized boolean full(){
		if((tail+1)%size==head) return true;
		else return false;
	}
	//-----------------------------------------------------------------		
	synchronized boolean store(T t){
		if(full()){
			System.out.println(name+" Buffer Full. Dropping Packet ...");
			return false;
		}
		else{
			tail=(tail+1)%size;
			data[tail]=t;						
			return true;
		}		
	}
	//-----------------------------------------------------------------		
	synchronized T get(){
		if(empty()){
			System.out.println(name+" Buffer Empty.");
			return null;
		}
		else {
		head=(head+1)%size;
		return data[head];		
		}
	}	
	//=======================================================	
}
