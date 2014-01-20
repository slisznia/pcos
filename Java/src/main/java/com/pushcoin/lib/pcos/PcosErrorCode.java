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

public enum PcosErrorCode
{
	/**
	 * Error code constants.
	 */
	ERR_INTERNAL_ERROR(100),
	ERR_MALFORMED_MESSAGE(101), 
	ERR_INCOMPATIBLE_REQUEST(102),
	ERR_BAD_MAGIC(102),
	ERR_ARG_OUT_OF_RANGE(103),
	ERR_BLOCK_NOT_FOUND(104),
	ERR_BAD_CHAR_ENCODING(105);
	
	public int code;
	
	private PcosErrorCode(int code)
	{
		this.code = code;
	}
}
