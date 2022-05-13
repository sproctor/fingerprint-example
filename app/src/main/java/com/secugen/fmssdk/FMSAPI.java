package com.secugen.fmssdk;

public class FMSAPI {

	public static final byte PACKET_HEADER_SIZE = 12;
	public static final byte IMAGE_SIZE_FULL = 0x01;
	public static final byte IMAGE_SIZE_HALF = 0x02;
	
	public static final byte CMD_GET_VERSION = 0x05;
	public static final byte CMD_FP_REGISTER_START = 0x50;
	public static final byte CMD_FP_REGISTER_END = 0x51;
	public static final byte CMD_FP_DELETE = 0x54;
	public static final byte CMD_FP_VERIFY = 0x55;
	public static final byte CMD_FP_IDENTIFY = 0x56;
	public static final byte CMD_FP_CAPTURE = 0x43;

	public static final byte CMD_DB_GET_RECCOUNT= 0x70;
	public static final byte CMD_DB_ADD_REC = 0x71;
	public static final byte CMD_DB_GET_REC = 0x73;
	public static final byte CMD_DB_GET_FIRSTREC = 0x74;
	public static final byte CMD_DB_GET_NEXTREC = 0x75;
	public static final byte CMD_DB_DELETE_ALL = 0x76;

	public static final byte CMD_FP_AUTO_IDENTIFY_START = (byte) 0xA1;
	public static final byte CMD_FP_AUTO_IDENTIFY_STOP = (byte) 0xA2;

	//ERROR CODES
	public static final byte ERR_NONE = 0x00;                // Normal operation
	public static final byte ERR_FLASH_OPEN = 0x01;          //  Flash memory error
	public static final byte ERR_SENSOR_OPEN = 0x02;         //  Sensor initialization failed
	public static final byte ERR_REGISTER_FAILED = 0x03;     //  Fingerprint registration failed
	public static final byte ERR_VERIFY_FAILED = 0x04;       //  Fingerprint verification failed
	public static final byte ERR_ALREADY_REGISTERED_USER = 0x05; //  User ID already exists
	public static final byte ERR_USER_NOT_FOUND = 0x06;      //  User ID is not found
	public static final byte ERR_TIME_OUT = 0x08;            //  Failed to capture fingerprint in preset time
	public static final byte ERR_DB_FULL = 0x09;             //  SDA database is full
	public static final byte ERR_WRONG_USERID = 0x0A;        //  Wrong user ID
	public static final byte ERR_DB_NO_DATA = 0x0B;          //  SDA database is empty
	public static final byte ERR_FUNCTION_FAIL = 0x10;       //  Wrong usage of command packet
	public static final byte ERR_INSUFFICIENT_DATA = 0x11;   //  Wrong length value of Extra Data
	public static final byte ERR_FLASH_WRITE_ERROR = 0x12;   //  Flash write error
	public static final byte ERR_INVALID_PARAM = 0x14;       //  Parameter value is not invalid
	public static final byte ERR_AUTHENTICATION_FAIL = 0x17; //  Master identification failed or needs to master authentication
	public static final byte ERR_IDENTIFY_FAILED = 0x1B;     //  Fingerprint identification failed
	public static final byte ERR_CHECKSUM_ERR = 0x28;        //  Wrong check sum
	public static final byte ERR_INVALID_FPRECORD = 0x30;    //  Record format is invalid
	public static final byte ERR_UNKNOWN_COMMAND = (byte)0xFF;//  Unknown command
	
	
	public static byte[] cmdGetVersion()
	{
		FMSHeader header = new FMSHeader();
		header.pkt_command = CMD_GET_VERSION;
		header.setCheckSum();
		return(header.get());
	}

	public static byte[] cmdFPRegisterStart(int userID, boolean isAdmin)
	{
		FMSHeader header = new FMSHeader();
		header.pkt_command = CMD_FP_REGISTER_START;
		header.pkt_param1 = (short) userID;
		if (isAdmin)
		    header.pkt_param2 = 1;
		else
		    header.pkt_param2 = 0;
		header.setCheckSum();
		return(header.get());
	}

	public static byte[] cmdFPRegisterEnd()
	{
		FMSHeader header = new FMSHeader();
		header.pkt_command = CMD_FP_REGISTER_END;
		header.setCheckSum();
		return(header.get());
	}

	public static byte[] cmdFPDelete(int userID)
	{
		FMSHeader header = new FMSHeader();
		header.pkt_command = CMD_FP_DELETE;
		header.pkt_param1 = (short) userID;
		header.setCheckSum();
		return(header.get());
	}		
	public static byte[] cmdFPVerify(int userID)
	{
		FMSHeader header = new FMSHeader();
		header.pkt_command = CMD_FP_VERIFY;
		header.pkt_param1 = (short) userID;
		header.setCheckSum();
		return(header.get());
	}		
	public static byte[] cmdFPIdentify()
	{
		FMSHeader header = new FMSHeader();
		header.pkt_command = CMD_FP_IDENTIFY;
		header.setCheckSum();
		return(header.get());
	}
	public static byte[] cmdFPCapture(byte size)
	{
		FMSHeader header = new FMSHeader();
		header.pkt_command = CMD_FP_CAPTURE;
		if (size == IMAGE_SIZE_FULL)
			header.pkt_param1 = 0x0001;
		else
			header.pkt_param1 = 0x0002;
		header.setCheckSum();
		return(header.get());
	}

	public static byte[] cmdFPCaptureUseWSQ(byte size)
	{
		FMSHeader header = new FMSHeader();
		header.pkt_command = CMD_FP_CAPTURE;
		if (size == IMAGE_SIZE_FULL)
			header.pkt_param1 = 0x0101;
		else
			header.pkt_param1 = 0x0102;
		header.pkt_param2 = 0x0200;
		header.setCheckSum();
		return(header.get());
	}

	public static String parseResponse(byte[] buffer)
	{
		
		byte checksum = FMSHeader.GetCheckSum(buffer, 11);
		if (checksum != buffer[11])
			return new String("Cksm Err: [" + Integer.toHexString((int) buffer[11]) + "][" + Integer.toHexString((int) checksum) + "]");
		else
		{		
			FMSHeader header = new FMSHeader(buffer);
			switch(buffer[1])
			{
				case CMD_GET_VERSION:
					if (buffer[10] != ERR_NONE)
						return new String("Error: [" + Integer.toHexString((int) buffer[10])  + "]");
					else
					{
						String versionMajor = Integer.toHexString(header.pkt_param1);
						String versionMinor = Integer.toHexString(header.pkt_param2);
						return new String("F/W ver " + versionMajor + "." + versionMinor);
					}
				case CMD_FP_REGISTER_START:
					switch(buffer[10])
					{
						case ERR_NONE:
							return new String("Capture 1 OK. Place same finger and click Register 2");
						case ERR_ALREADY_REGISTERED_USER:
							return new String("User " + Integer.valueOf(header.pkt_param1) + " already registered");			
						default:
							return new String("Error: [" + Integer.toHexString((int) buffer[10])  + "]");
					}
				case CMD_FP_REGISTER_END:
					if (buffer[10] != ERR_NONE)
						return new String("Error: [" + Integer.toHexString((int) buffer[10])  + "]");
					else
						return new String("Registration was successful");			
				case CMD_FP_DELETE:
					switch(buffer[10])
					{
						case ERR_NONE:
							return new String("User " + Integer.valueOf(header.pkt_param1) + " deleted");
						case ERR_USER_NOT_FOUND:
							return new String("User " + Integer.valueOf(header.pkt_param1) + " not found");			
						default:
							return new String("Error: [" + Integer.toHexString((int) buffer[10])  + "]");
					}
				case CMD_FP_VERIFY:
					switch(buffer[10])
					{
						case ERR_NONE:
							return new String("User " + Integer.valueOf(header.pkt_param1) + " verified. Score:[" + header.pkt_param2 + "]");
						case ERR_VERIFY_FAILED:
							return new String("User " + Integer.valueOf(header.pkt_param1) + " not verified. Score:[" + header.pkt_param2 + "]");
						case ERR_USER_NOT_FOUND:
							return new String("User " + Integer.valueOf(header.pkt_param1) + " not found.");							
						default:
							return new String("Error: [" + Integer.toHexString((int) buffer[10])  + "]");
					}
				case CMD_FP_IDENTIFY:
					switch(buffer[10])
					{
						case ERR_NONE:
							return new String("User " + header.pkt_param1 + " identified. Score:[" + header.pkt_param2 + "]" );
						case ERR_IDENTIFY_FAILED:
							return new String("User not found.");
						default:
							return new String("Error: [" + Integer.toHexString((int) buffer[10])  + "]");
					}
				case CMD_FP_CAPTURE:
					switch(buffer[10])
					{
						case ERR_NONE:
							return new String("Capture success" );
						default:
							return new String("Error: [" + Integer.toHexString((int) buffer[10])  + "]");
					}
				default:
					return new String("Unknown Response");
			}
		}
	}
	
	
}
