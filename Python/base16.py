#!/usr/bin/python

#	Copyright (c) 2012 Minta, Inc.
#
#	GNU General Public Licence (GPL)
#	
#	This program is free software; you can redistribute it and/or modify it under
#	the terms of the GNU General Public License as published by the Free Software
#	Foundation; either version 2 of the License, or (at your option) any later
#	version.
#	This program is distributed in the hope that it will be useful, but WITHOUT
#	ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
#	FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
#	details.
#	You should have received a copy of the GNU General Public License along with
#	this program; if not, write to the Free Software Foundation, Inc., 59 Temple
#	Place, Suite 330, Boston, MA  02111-1307  USA
#
__author__	 = '''Slawomir Lisznianski <sl@pushcoin.com>'''

#	Alphabet Table
#	==============
#  0 => A
#  1 => C
#  2 => E
#  3 => F
#  4 => H
#  5 => K
#  6 => L
#  7 => N
#  8 => P		
#  9 => R
# 10 => T
# 11 => X
# 12 => Y
# 13 => 3
# 14 => 4
# 15 => 5
# 16 => 7

from optparse import OptionParser, OptionError
import random, math

class	Pcos16Aalphabet():
	 '''Stores the PCOS-16 Alphabet'''
	 def __init__(self):
		self.val_to_symbol	= [ 'A', 'C', 'E', 'F', 'H', 'K', 'L', 'N', 'P', 'R', 'T', 'X', 'Y', '4', '5', '7' ]
		self.symbol_to_val	= { 'A': 0, 'C': 1, 'E': 2, 'F': 3, 'H': 4, 'K': 5, 'L': 6, 'N': 7, 'P': 8, 'R': 9, 'T': 10, 'X': 11, 'Y': 12, '4': 13, '5': 14, '7': 15 }

alphabet__ = Pcos16Aalphabet()

def encode(bytestr):
	encoding=[]
	for	bt in bytestr:
		encoding.append( alphabet__.val_to_symbol[ ord(bt) >> 4 ] )
		encoding.append( alphabet__.val_to_symbol[ ord(bt) & 0x0f ] )
	return ''.join(encoding)

def decode(pretty):
	sz = len(pretty)
	if sz % 2 != 0:
		raise TypeError("Wrong input length to be PCOS-16" )

	bytestr	= bytearray()
	for	i in xrange(0,sz,2):
		oktet1 = alphabet__.symbol_to_val.get(pretty[i], None)
		if oktet1 == None:
			raise	TypeError("Character not in PCOS-16 alphabet" )

		oktet2 = alphabet__.symbol_to_val.get(pretty[i+1], None)
		if oktet2 == None:
			raise	TypeError("Character not in PCOS-16 alphabet" )

		bytestr.append( (oktet1 << 4) | oktet2 )
	return	bytestr


def randbytes(n):
	return [chr(random.getrandbits(8)) for i in range(n)]


if __name__ == "__main__":
	# program arguments
	usage = "usage: %prog [options]"
	version = "PushCoin ID Generator v1.0"
	parser = OptionParser(usage, version = version)
	parser.add_option("-L", "--length", dest="id_size", type="int", action="store", default=10, help="length of generated ID")
	parser.add_option("-N", "--count", dest="count", type="int", action="store", default=5, help="number of IDs to generate")
	
	(opts, args) = parser.parse_args()
	
	print version

	for i in range(opts.count):
		random_bytes = randbytes(int(math.ceil(opts.id_size/2.0)))
		print (encode( random_bytes )[:opts.id_size])
	
	exit(0)
