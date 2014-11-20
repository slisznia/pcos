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

public class ProtocolTag 
{
	public static final int MAX_MESSAGE_ID_LEN = 128;
	public static final int MAX_BLOCK_ID_LENGTH = 64;
	public static final int MIN_MESSAGE_LENGTH = 7;
	public static final byte[] PROTOCOL_MAGIC = new byte[]{'P','C','O','S'};
	public static final int PROTOCOL_MAGIC_LEN = 4;
	public static final byte PROTOCOL_FLAGS = 0x0;
	public static final String PROTOCOL_CHARSET = "UTF-8";
}
