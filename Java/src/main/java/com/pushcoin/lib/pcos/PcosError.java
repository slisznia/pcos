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

public class PcosError extends Exception 
{
	/**
	 * Exception type thrown from PCOS functions when parsing or writing data.
	 */
	private static final long serialVersionUID = 1L;
	private final int code_;
	
	public PcosError(PcosErrorCode ercode, String reason)
	{
		super(reason);
		code_ = ercode.code;
	}

	public int getCode() {
		return code_;
	}
}
