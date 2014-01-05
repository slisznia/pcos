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

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public class DocumentReader implements InputDocument 
{
	private String magic_;
	private byte pcosFlags_;
	private String documentName_;
	private long blockCount_;
	private Map<String, InputBlock> blocks_ = new TreeMap<String, InputBlock>();

	private final class BlockMeta 
	{
		public long length;
		public String name;
	}

	@Override
	public String getMagic()
	{
		return magic_;
	}

	@Override
	public String getDocumentName()
	{
		return documentName_;
	}

	@Override
	public long getBlockCount()
	{
		return blockCount_;
	}

	@Override
	public Map<String, InputBlock> getBlocks() 
	{
		return Collections.unmodifiableMap(blocks_);
	}

	public DocumentReader(byte[] input, int length) throws PcosError
	{
		parseBytes(input, length);
	}

	public DocumentReader(byte[] input) throws PcosError
	{
		parseBytes(input, input.length);
	}

	@Override
	public InputBlock getBlock(String name) throws PcosError 
	{
		InputBlock blk = blocks_.get(name);
		if (blk == null)
			throw new PcosError( PcosErrorCode.ERR_BLOCK_NOT_FOUND, "Block not found in PCOS message: " + name);
		return blk;
	}

	private void parseBytes(byte[] input, final int length) throws PcosError
	{
		InputBlock inblock = new BlockReader( input, 0, length, "Hd" );
		// read PCOS magic
		byte[] magic = inblock.readBytes( ProtocolTag.PROTOCOL_MAGIC_LEN );
		if (! Arrays.equals(ProtocolTag.PROTOCOL_MAGIC, magic))
			throw new PcosError( PcosErrorCode.ERR_BAD_MAGIC, "Not a PCOS message; bad magic" );
		magic_ = new String(magic);

		// protocol flags
		pcosFlags_ = inblock.readByte();

		// message (doc) name
		documentName_ = inblock.readString( ProtocolTag.MAX_MESSAGE_ID_LEN );
		
		// block count
		blockCount_ = inblock.readUint();

		// Enumerating blocks is a two-pass process -- first, we get their names and lengths,
		// then we can arrive at the beginning of the data segment.
		AbstractList<BlockMeta> stageBlocks = new ArrayList<BlockMeta>();
		
		// Pass One: enumerate blocks
		for (int i = 0; i < blockCount_; ++i)
		{
			BlockMeta blk = new BlockMeta();
			blk.name = inblock.readString( ProtocolTag.MAX_BLOCK_ID_LENGTH );
			blk.length = inblock.readUint();
			stageBlocks.add(blk);
		}

		// at this point remember where data-segment starts,
		// which is the location of the first block
		int block_offset = inblock.readingPosition();

		// Pass Two: populate block positions within payload
		for (BlockMeta blk : stageBlocks)
		{
			// store the block meta-record in the directory
			blocks_.put( blk.name, new BlockReader(input, block_offset, (int)blk.length, blk.name) );

			// update position for the next block
			block_offset += blk.length;
		}
			
		if ( block_offset > length ) {
			throw new PcosError( PcosErrorCode.ERR_MALFORMED_MESSAGE, "Incomplete message or wrong block-meta info -- blocks couldn't fit in the received payload" );
		}
	}

}
