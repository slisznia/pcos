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

import com.pushcoin.lib.pcos.*;
import java.util.Map.Entry;
import java.util.Arrays;

public final class TestPcos
{
	static final byte[] rawbytes = {0xa, 0x2, (byte) 0xff};
	static final String varstr = "variable string";

	private static byte[] testWriteDatatypes() throws PcosError
	{
		OutputBlock bo = new BlockWriter( "Bo" );
		bo.writeByte( 44 );
		bo.writeBytes( rawbytes );
		bo.writeBool( false );
		bo.writeBool( true );

		bo.writeUint( 127 );
		bo.writeUint( 128 );
		bo.writeInt( 63 );
		bo.writeInt( 64 );

		bo.writeUlong( 127 );
		bo.writeUlong( 128 );
		bo.writeLong( 63 );
		bo.writeLong( 64 );
		bo.writeDouble(3.14);

		bo.writeByteStr(rawbytes);
		bo.writeString(varstr);
		
		OutputDocument doc = new DocumentWriter("Test");
		doc.addBlock(bo);
		return doc.toBytes();
	}
	
	private static void testReadDatatypes( byte[] input ) throws PcosError
	{
		InputDocument doc = new DocumentReader( input );
		System.out.println( "magic: " + doc.getMagic() );
		System.out.println( "name: " + doc.getDocumentName() );
		System.out.println( "blocks: ");
		
		for (Entry<String, InputBlock> entry : doc.getBlocks().entrySet())
		{
			InputBlock blk = entry.getValue();
			System.out.println( "  - " + blk.name() + " => " + blk.size() + " (bytes)" );
		}
		
		InputBlock bo = doc.getBlock("Bo");
		
		assert bo.readByte() == 44;
		assert Arrays.equals(bo.readBytes(rawbytes.length), rawbytes );
		assert bo.readBool() == false;
		assert bo.readBool() == true;

		assert bo.readUint() == 127;
		assert bo.readUint() == 128;
		assert bo.readInt() == 63;
		assert bo.readInt() == 64;

		assert bo.readUlong() == 127;
		assert bo.readUlong() == 128;
		assert bo.readLong() == 63;
		assert bo.readLong() == 64;
		assert (Math.abs(bo.readDouble() - 3.14) < 0.001);

		assert Arrays.equals(bo.readByteStr(rawbytes.length), rawbytes );
		assert bo.readString(varstr.length()).equals( varstr );
	}
	
	/**
	 * @param args
	 */
	public static void main( String[] args ) 
	{
		try 
		{
			final byte[] input = testWriteDatatypes();
			StringBuilder sb = new StringBuilder();
			for (byte b : input) 
			{
				sb.append(String.format("%02X ", b));
			}
			System.out.println("size=" + input.length + ", val=" + sb.toString());
			testReadDatatypes( input );

			System.out.println( "All checks out!" );
			System.exit(0);
		} 
		catch (PcosError e) 
		{
			e.printStackTrace();
			System.err.println( "PCOS error: " + e.getMessage() );
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.err.println( "Basic error: " + e );
		}
	}
}
