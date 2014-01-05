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

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.io.UnsupportedEncodingException;

public class BlockReader implements InputBlock 
{
	private final int TYPE_WIRE_SIZE_DOUBLE = 8;
	private final String MALFORMED_ERR_ = "run out of input bytes - incomplete or corrupted message";
	private final PcosError malformed_ = new PcosError( PcosErrorCode.ERR_MALFORMED_MESSAGE, MALFORMED_ERR_ );
	private final byte[] input_;
	private final int end_, beg_;
	private final String name_;
	private int offset_;
	
	public BlockReader(byte[] input, int offset, int len, String name)
	{
		input_ = input;
		beg_ = offset;
		end_ = beg_ + len;
		name_ = name;
		offset_ = offset;
	}
	
	@Override
	public boolean readBool() throws PcosError 
	{
		return readByte() != 0;
	}

	@Override
	public byte readByte() throws PcosError 
	{
		if (end_ - offset_ > 0)
		{
			return input_[offset_++];
		}

		throw malformed_;
	}

	@Override
	public byte[] readBytes(long length) throws PcosError 
	{
		if (end_ - offset_ >= length)
		{
			byte[] val = Arrays.copyOfRange(input_, offset_, (int) (offset_+ length));
			offset_ += length;
			return val;
		}

		throw malformed_;
	}

	@Override
	public byte[] readByteStr(long maxlen) throws PcosError
	{
		long length = readUint();
		if (maxlen != 0 && length > maxlen)
			throw new PcosError( PcosErrorCode.ERR_ARG_OUT_OF_RANGE, "input byte-sequence exceeds max length" );
		return readBytes(length);
	}

	@Override
	public double readDouble() throws PcosError
	{
		return ByteBuffer.wrap(readBytes(TYPE_WIRE_SIZE_DOUBLE)).getDouble();
	}

	private long readVarInt( int max_octets ) throws PcosError
	{
		long val = 0;
		boolean seen_end = false;
		while (max_octets > 0)
		{
			int octet = readByte();
			val |= (octet & 0x7f); 
			// check if there is more...
			seen_end = ( (octet & 0x80) == 0 );
			if (seen_end) {
				break;
			}
			else
			{
				val <<= 7;
				max_octets--;
			}
		}

		if (!seen_end) {
			throw malformed_;
		}

		return val;
	}

	@Override
	public int readInt() throws PcosError
	{
		// signed int is encoded as unsigned int
		long val = readUint();
		// ..but requires un-ZigZag
		return (int) ((val >> 1) ^ (-(val & 1)));
	}

	@Override
	public long readUint() throws PcosError
	{
		return readVarInt( 5 ); // 5 => max bytes uint can take on the wire
	}
	
	@Override
	public long readLong() throws PcosError
	{
		// signed int is encoded as unsigned int
		long val = readUlong();
		// ..but requires un-ZigZag
		return (val >> 1) ^ (-(val & 1));
	}

	@Override
	public long readUlong() throws PcosError
	{
		return readVarInt( 10 ); // 5 => max bytes uint can take on the wire
	}

	@Override
	public String readString(long maxlen) throws PcosError
	{
		try {
		byte[] encoded_str = readByteStr( maxlen );
		return new String(encoded_str, ProtocolTag.PROTOCOL_CHARSET);
		} catch (UnsupportedEncodingException e)	{
			throw new PcosError( PcosErrorCode.ERR_BAD_CHAR_ENCODING, "input string decoding error" );
		}
	}

	@Override
	public int readingPosition() 
	{
		return offset_;
	}

	@Override
	public String name() 
	{
		return name_;
	}

	@Override
	public int size() 
	{
		return end_ - beg_;
	}
}
