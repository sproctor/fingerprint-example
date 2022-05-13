package com.secugen.fmssdk;

public class FMSHeader {

	//12 byte U20-BF header
	public byte  pkt_class;
	public byte  pkt_command;
	public short pkt_param1;
	public short pkt_param2;
	public short pkt_datasize1;
	public short pkt_datasize2;
	public byte  pkt_error;
	public byte  pkt_checksum;
	
	public FMSHeader()
	{
		pkt_class = 0x00;
		pkt_command = 0x00;
		pkt_param1 = 0x00;
		pkt_param2 = 0x00;
		pkt_datasize1 = 0x00;
		pkt_datasize2 = 0x00;
		pkt_error = 0x00;
		pkt_checksum = 0x00;	
	}
	
	public FMSHeader(byte[] buffer)
	{
		set(buffer, false);
	}

	public FMSHeader(byte[] buffer, boolean calcChecksum)
	{
		set(buffer, calcChecksum);
	}
	
	public void set(byte[] buffer, boolean calcChecksum)
	{
		pkt_class = buffer[0];
		pkt_command = buffer[1];
		pkt_param1 = (short)((buffer[2]&0xFF) | (buffer[3]&0xFF) << 8);
		pkt_param2 = (short)((buffer[4]&0xFF) | (buffer[5]&0xFF) << 8);
		pkt_datasize1 = (short)((buffer[6]&0xFF) | (buffer[7]&0xFF) << 8);
		pkt_datasize2 = (short)((buffer[8]&0xFF) | (buffer[9]&0xFF) << 8);
		pkt_error = buffer[10];
		if (calcChecksum)
			pkt_checksum = GetCheckSum(get(),11);
		else
			pkt_checksum = buffer[11];
	}
	
	public byte[] get()
	{
		byte[] buffer = new byte[12];
		buffer[0] = pkt_class;
		buffer[1] = pkt_command;
		buffer[2] = (byte) pkt_param1;
		buffer[3] = (byte) (pkt_param1 >> 8);
		buffer[4] = (byte) pkt_param2;
		buffer[5] = (byte) (pkt_param2 >> 8);
		buffer[6] = (byte) pkt_datasize1;
		buffer[7] = (byte) (pkt_datasize1 >> 8);
		buffer[8] = (byte) pkt_datasize2;
		buffer[9] = (byte) (pkt_datasize2 >> 8);
		buffer[10] = pkt_error;
		buffer[11] = pkt_checksum;
		return buffer;
	}
	
	public static byte GetCheckSum(byte[] buffer, int buffLength)
	{
		byte checksum = 0;
		int checksumCalc;
		for (int i=0; i< buffLength; ++i)
		{
			checksumCalc = buffer[i] + checksum;
			checksum = (byte) (checksumCalc & 0xFF);
		}
		return checksum;		
	}

	public void setCheckSum()
	{
		pkt_checksum = GetCheckSum(get(),11);
	}

	public boolean isWSQ() {
		return (pkt_param1 & 0xFF00) != 0;
	}

	public boolean isFullSize() {
		return (pkt_param1 & 0xFF) == 0x01;
	}
}

