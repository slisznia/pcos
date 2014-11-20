// Copyright (c) 2012 PushCoin, Inc.
//
// GNU General Public Licence (GPL)
// 
// This program is free software; you can redistribute it and/or modify it under
// the terms of the GNU General Public License as published by the Free Software
// Foundation; either version 2 of the License, or (at your option) any later
// version.
// This program is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
// FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
// details.
// You should have received a copy of the GNU General Public License along with
// this program; if not, write to the Free Software Foundation, Inc., 59 Temple
// Place, Suite 330, Boston, MA  02111-1307  USA
//
// __author__  = '''Slawomir Lisznianski <sl@pushcoin.com>'''
	
package com.pushcoin.lib.pcos;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class BlockWriter implements OutputBlock 
{
	private final String name_;
	private final PcosError malformed_ = new PcosError( PcosErrorCode.ERR_MALFORMED_MESSAGE, "serialization error" );
	private DataOutputStream output_;
	private ByteArrayOutputStream streambuf_;

	public BlockWriter( String blockName, OutputStream dst )
	{
		name_ = blockName;
		output_ = new DataOutputStream( dst );
	}

	public BlockWriter( String blockName )
	{
		name_ = blockName;	
		streambuf_ = new ByteArrayOutputStream();
		output_ = new DataOutputStream( streambuf_ );
	}

	@Override
	public String name() 
	{
		return name_;
	}

	@Override
	public int size() 
	{
		return output_.size();
	}

	@Override
	public void writeBool(boolean b) throws PcosError 
	{
		try
		{
			output_.writeBoolean(b);
		} catch (IOException e)	{
			throw malformed_;
		}
	}

	@Override
	public void writeByte(int v) throws PcosError 
	{
		try
		{
			output_.write(v);
		} catch (IOException e)	{
			throw malformed_;
		}
	}

	@Override
	public void writeBytes(byte[] v) throws PcosError 
	{
		try {
			output_.write(v);
		} catch (IOException e)	{
			throw malformed_;
		}
	}

	@Override
	public void writeDouble(double v) throws PcosError 
	{
		try {
			output_.writeDouble(v);
		} catch (IOException e)	{
			throw malformed_;
		}
	}

	@Override
	public void writeByteStr(byte[] s) throws PcosError 
	{
		if (s != null)
		{
			writeUint(s.length);
			writeBytes(s);
		}
		else {
			writeUint(0);
		}
	}

	@Override
	public void writeString(String s) throws PcosError 
	{
		try
		{
			/* PCOS uses UTF-8 encoding on the wire */
			byte[] encoded_str = null;
			if (s != null) {
				encoded_str = s.getBytes(ProtocolTag.PROTOCOL_CHARSET);
			}
			writeByteStr(encoded_str);
		} catch (UnsupportedEncodingException e)	{
			throw new PcosError( PcosErrorCode.ERR_BAD_CHAR_ENCODING, "output string encoding error" );
		}
	}

	@Override
	public void writeInt(int val) throws PcosError 
	{
		// signed numbers are converted to unsigned according to ZigZag
		long zz = ( (val << 1) ^ (val >> 31) );
		writeVarInt( zz );
	}

	@Override
	public void writeUint(long val) throws PcosError 
	{
		writeVarInt(val);
	}

	@Override
	public void writeLong(long val) throws PcosError 
	{
		// signed numbers are converted to unsigned according to ZigZag
		long zz = ( (val << 1) ^ (val >> 63) );
		writeVarInt( zz );
	}

	@Override
	public void writeUlong(long v) throws PcosError 
	{
		writeVarInt(v);
	}
	
	private final int VARINT_BUFF_POS_LAST_ = 9;
	private byte[] varIntBuf_ = new byte[ VARINT_BUFF_POS_LAST_+1 ];
	
	private void writeVarInt( long val ) throws PcosError
	{
		int pos = VARINT_BUFF_POS_LAST_;
		while (val > 0x7f)
		{
			int octet = (int) (val & 0x7f);
			// first one doesn't need continuation bit --
			// after reversing it will be last
			if ( pos !=  VARINT_BUFF_POS_LAST_ )	{
				octet |= 0x80;
			}

			varIntBuf_[pos--] = (byte) octet;
			val >>= 7;
		}

		// deal with last (or only) octet
		if ( pos ==  VARINT_BUFF_POS_LAST_ ) 
		{
			try	{
				output_.write( (int) val );
			} catch (IOException e)	{
				throw malformed_;
			}
		}
		else 
		{
			val |= 0x80;
			varIntBuf_[pos] = (byte)val;
			try {
				output_.write(varIntBuf_, pos, varIntBuf_.length - pos);
			} catch (IOException e)	{
				throw malformed_;
			}
		}
	}

	@Override
	public byte[] toBytes() throws PcosError
	{
		if (streambuf_ != null) {
			return streambuf_.toByteArray();
		}
		throw new PcosError( PcosErrorCode.ERR_INTERNAL_ERROR, "no access to the underlying datastore" );
	}
}
