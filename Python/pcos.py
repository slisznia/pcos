# Copyright (c) 2012 Minta, Inc.
#
# GNU General Public Licence (GPL)
# 
# This program is free software; you can redistribute it and/or modify it under
# the terms of the GNU General Public License as published by the Free Software
# Foundation; either version 2 of the License, or (at your option) any later
# version.
# This program is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
# FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
# details.
# You should have received a copy of the GNU General Public License along with
# this program; if not, write to the Free Software Foundation, Inc., 59 Temple
# Place, Suite 330, Boston, MA  02111-1307  USA
#
__author__  = '''Slawomir Lisznianski <sl@pushcoin.com>'''

import binascii, struct, ctypes

# This is a minimum length (in bytes) for a PCOS serialized object:
#
MIN_MESSAGE_HEADER_LENGTH = 8

# safety limits
MAX_MESSAGE_ID_LENGTH = 128
MAX_BLOCK_ID_LENGTH = 64
MAX_BLOCK_COUNT = 8
MAX_BLOCK_LENGTH = 10240

# PCOS object mime type
PCOS_MIME_TYPE = 'application/pcos'

# Largest unsigned long
MAX_UINT=4294967295
MAX_ULONG=18446744073709551615

# PushCoin protocol identifier (aka "magic")
PROTOCOL_MAGIC = b'PCOS'
PROTOCOL_MAGIC_LEN = len(PROTOCOL_MAGIC)
PROTOCOL_FLAGS=0x0

# PCOS parser error codes
ERR_INTERNAL_ERROR = 100
ERR_MALFORMED_MESSAGE = 101
ERR_BAD_MAGIC = 102
ERR_ARG_OUT_OF_RANGE = 103
ERR_BLOCK_NOT_FOUND = 104
ERR_BAD_CHAR_ENCODING = 105


# Convert integer to a varint, return a bytearray
def _to_varint( val ):
	dat = bytearray()
	while val > 0x7f:
		octet = val & 0x7f
		
		# first one doesn't need continuation bit --
		# after reversing it will be last
		if dat:
			octet |= 0x80

		dat.append(octet)
		val >>= 7

	# deal with last (or only) octet
	if dat:
		val |= 0x80
		dat.append( val )
		# obey network order
		dat.reverse()
	else:
		dat.append( val )

	return bytes(dat)


class PcosError( Exception ):
	""" Basis for all exceptions thrown from the PCOS codec."""

	def __init__(self, code, what = ''):
		self.code = code
		self.what = what

	def __str__(self):
		return repr("code=%s;what=%s" % (self.code, self. what) )


class BlockMeta:
	"""Stores meta information about a block found in the data-segment"""
	pass


class Doc:
	"""Parses binary data, presumably PCOS-encoded, and constructs a lightweight document."""

	def __init__( self, data = None, name = None ): 
		"""Constructs PCOS from binary data."""

		self.message_id = name 

		# map of blocks, such that for a given block-name, we can quickly access its data
		self.blocks = { }

		if data:
			payload = parse_block(data, name, 'Hd')
			if payload.size() < MIN_MESSAGE_HEADER_LENGTH:
				raise PcosError( ERR_MALFORMED_MESSAGE, 'Payload too small for PCOS message' )

			# parse the message header
			self.magic = payload.read_bytes( PROTOCOL_MAGIC_LEN )

			# check if magic matches
			if self.magic != PROTOCOL_MAGIC:
				raise PcosError( ERR_BAD_MAGIC, "Not a PCOS message; bad magic" )

			# PCOS flags
			self.pcos_flags = payload.read_byte()

			# message ID
			self.message_id = payload.read_string( MAX_MESSAGE_ID_LENGTH )

			# block count
			self.block_count = payload.read_uint()
			if self.block_count > MAX_BLOCK_COUNT:
				raise PcosError( ERR_ARG_OUT_OF_RANGE, 'Block count exceeds max of %s (%s)' % (MAX_BLOCK_COUNT, self.block_count) )

			# data appears to be "one of ours", store it
			self.data = data
		
			# Enumerating blocks is a two-pass process -- first, we get their names and lengths,
			# then we can arrive at the beginning of the data segment.
			stage_blocks = []

			# Pass One: enumerate blocks
			for i in xrange(0, self.block_count):
				blk = BlockMeta()
				# block name
				blk.name = payload.read_string( MAX_BLOCK_ID_LENGTH )
				# block length
				blk.length = payload.read_uint()
				if blk.length > MAX_BLOCK_LENGTH:
					raise PcosError( ERR_ARG_OUT_OF_RANGE, 'Block size exceeds max of %s (%s)' % (MAX_BLOCK_LENGTH, blk.length) )
				# store the block-meta
				stage_blocks.append(blk)

			# at this point remember where data-segment starts,
			# which is the location of the first block
			block_offset = payload.reading_position()

			# Pass Two: populate block positions within payload
			for blk in stage_blocks:
				# mark beginning of this block
				blk.start = block_offset

				# store the block meta-record in the directory
				self.blocks[blk.name] = blk

				# update position for the next block
				block_offset += blk.length
				
			if block_offset > payload.size():
				raise PcosError( ERR_MALFORMED_MESSAGE, "Incomplete message or wrong block-meta info; blocks cannot fit in payload" )


	def block( self, name, optional=False ):
		"""Returns the block iterator for a given block name."""

		meta = self.blocks.get( name, None )
		if meta:
			return create_input_block(self, meta)
		else:
			if optional:
				return None # Oops, block not found!
			else:
				raise PcosError( ERR_BLOCK_NOT_FOUND, "Required block '%s' not found in message" % name )


	def add( self, block ):
		"""Add a block to the data-segment."""

		self.blocks[ block.name() ] = block
		

	def as_bytearray( self ):
		"""Returns encoded byte-stream."""

		# buffer to store serialized data
		payload = bytearray()

		# protocol magic
		payload.extend(PROTOCOL_MAGIC)

		# PCOS flags
		payload.append(PROTOCOL_FLAGS)

		# message identifier
		payload.extend(_to_varint(len(self.message_id)))
		payload.extend(self.message_id)

		# number of blocks
		payload.extend(_to_varint( len(self.blocks) ))

		# block-metas
		for (name, b) in self.blocks.iteritems():
			# block name
			payload.extend(_to_varint(len(name)))
			payload.extend(name)

			# block size
			payload.extend(_to_varint( b.size() ))

		# block data
		for (name, b) in self.blocks.iteritems():
			payload.extend(b.as_bytearray())

		return bytes(payload)


	def _data_segment_size( self ):
		"""(Private) Returns size of all data blocks."""
		size = 0
		for (name, b) in self.blocks.iteritems():
			size += b.size()
		return size


def create_output_block( name ):
	"""Creates and initializes a block in 'output' mode."""

	blk = Block()
	blk.mode = "O"
	blk.data = bytearray()
	blk._name = name
	return blk
	

def create_input_block( doc, meta ):
	"""Creates and initializes a block in 'input' mode."""

	blk = Block()
	blk.mode = "I"
	blk.doc = doc
	blk._name = meta.name
	blk._length = meta.length
	blk._start = meta.start
	blk._offset = 0 # current reading cursor position
	return blk


def safe_str(obj):
    """ return the byte string representation of obj """
    try:
        return str(obj)
    except UnicodeEncodeError:
        # obj is unicode
        return unicode(obj).encode('unicode_escape')


class Block:
	"""Provides facilities for creating a new block or iterating over and parsing block data."""

	def __init__( self ):
		pass


	def as_bytearray( self ):
		'''Returns a Python string from the character array.'''
		if self.mode == 'I':
			return self.doc.data[self._start : self._start + self._length]
		else:
			return bytes(self.data)


	def size( self ):
		if self.mode == "I":
			return self._length
		else:
			return len(self.data)


	def name( self ):
		return self._name


	def reading_position( self ):
		return self._offset


	def read_byte( self ):
		assert self.mode == 'I'
		if self._length - self._offset > 0:
			octet = ord( self.doc.data[self._start + self._offset] )
			self._offset += 1
			return octet

		raise PcosError( ERR_MALFORMED_MESSAGE, 'run out of input bytes to read from - incomplete or corrupted message' )


	def read_bytes( self, size ):
		'''Reads exactly size-length bytes'''
		assert self.mode == 'I'
		if self._length - self._offset >= size:
			begin = self._start + self._offset
			dat = self.doc.data[ begin : begin + size]
			self._offset += size
			return bytes(dat)

		raise PcosError( ERR_MALFORMED_MESSAGE, 'run out of input bytes to read from - incomplete or corrupted message' )


	def read_bytestr( self, maxlen = None ):
		'''Reads up to maxlen bytes prefixed with size on the wire'''
		length = self.read_uint()
		if maxlen and length > maxlen:
			raise PcosError( ERR_ARG_OUT_OF_RANGE, 'input byte-sequence exceeds max length of %s (%s)' % (maxlen, length) )
		return self.read_bytes( length )


	def read_bool( self ):
		assert self.mode == 'I'
		return bool(self.read_byte())


	def read_varint( self, max_octets ):
		val = 0
		seen_end = False
		while max_octets > 0:
			octet = self.read_byte()
			val |= octet & 0x7f 
			# check if there is more...
			seen_end = bool( octet & 0x80 == 0 )
			if seen_end:
				break
			else:
				val <<= 7
				max_octets -= 1

		if not seen_end:
			raise PcosError( ERR_MALFORMED_MESSAGE, 'varint out of range' )

		return val
		

	def read_uint( self ):
		return self.read_varint( 5 ) # 5 => largest int can take on the wire


	def read_int( self ):
		# signed int is encoded as unsigned int
		val = self.read_uint()
		# ..but requires un-ZigZag
		return (val >> 1) ^ (-(val & 1))


	def read_ulong( self ):
		return self.read_varint( 10 ) # 10 => largest long can take up on the wire


	def read_long( self ):
		# signed long is encoded as unsigned long
		val = self.read_ulong()
		# ..but requires un-ZigZag
		return (val >> 1) ^ (-(val & 1))


	def read_double( self ):
		return struct.unpack("!d", self.read_bytes( 8 ))[0]


	def read_string( self, maxlen = None ):
		length = self.read_uint()
		if maxlen and length > maxlen:
			raise PcosError( ERR_ARG_OUT_OF_RANGE, 'string exceeds max-length of %s (%s)' % (maxlen, length) )
		return self.read_bytes( length ).decode("utf-8")


	def write_bool( self, val ):
		if val:
			self.write_byte(1)
		else:
			self.write_byte(0)


	def write_uint( self, val ):
		if val > MAX_UINT:
			raise PcosError( ERR_INTERNAL_ERROR, '%s does not fit in (unsigned) varint base-32' % val )
		self.write_bytes( _to_varint( val ) )


	def write_int( self, val ):
		# signed numbers are converted to unsigned according to ZigZag
		zz = (val << 1) ^ (val >> 31)
		self.write_uint( zz )


	def write_ulong( self, val ):
		if val > MAX_ULONG:
			raise PcosError( ERR_INTERNAL_ERROR, '%s does not fit in (unsigned) varint base-64' % val )
		self.write_bytes( _to_varint( val ) )


	def write_long( self, val ):
		# signed numbers are converted to unsigned according to ZigZag
		zz = (val << 1) ^ (val >> 63)
		self.write_ulong( zz )


	def write_double( self, val ):
		self.data.extend( struct.pack("!d", val) )


	def write_byte( self, val ):
		'''Writes a single byte (0-255) to the output buffer'''
		assert self.mode == 'O'
		self.data.append( val )


	def write_bytes( self, val ):
		'''Appends (encoded) bytes onto output buffer'''
		assert self.mode == 'O'
		self.data.extend( val )


	def write_bytestr( self, val ):
		'''Appends size-prefix then (encoded) bytes onto output buffer'''
		if val == None:
			self.write_uint( 0 )
			return

		self.write_uint( len(val) )
		self.write_bytes( val )


	def write_string( self, val ):
		'''Encodes string as UTF-8, then writes bytes'''
		self.write_bytestr( val.encode("utf-8") ) 


def parse_block(data, message_id, block_name):
	'''Returns a Block instance from raw block data'''
	blk = BlockMeta()
	blk.name = block_name
	blk.length = len(data)
	blk.start = 0

	doc = Doc( data = None, name = message_id )
	doc.data = data
	doc.blocks[block_name] = blk
	doc.magic = PROTOCOL_MAGIC
	doc.pcos_flags = PROTOCOL_FLAGS
	doc.length = blk.length
	doc.block_count = 1
	return doc.block(block_name)


def _reading_test_pong():
	"""Tests if parser handles Pong message correctly"""

	# `Pong' message, normally arriving on the wire
	msg = Doc( _writing_test_pong() )

	# jump to the block of interest
	tm = msg.block( 'Tm' )

	# read block field(s)
	tm_epoch = tm.read_ulong();

	assert tm_epoch == 1335795040

	
def _datatype_test():
	"""Test serialization and de-serialization of all primitive types"""

	rawbytes = binascii.unhexlify( '0a02ff' )
	varstr = 'variable string'
	
	bo_in = create_output_block( 'Bo' )
	bo_in.write_byte( 44 )
	bo_in.write_bytes( rawbytes )
	bo_in.write_bool( False )
	bo_in.write_bool( True )

	bo_in.write_uint( 127 ) # single octet
	bo_in.write_uint( 128 ) # two octets
	bo_in.write_int( 63 ) # single octet
	bo_in.write_int( 64 ) # two octets

	bo_in.write_ulong( 127 ) # single octet
	bo_in.write_ulong( 128 ) # two octets
	bo_in.write_long( 63 ) # single octet
	bo_in.write_long( 64 ) # two octets
	bo_in.write_double(3.14)

	bo_in.write_bytestr( rawbytes )
	bo_in.write_string( varstr )

	outgoing = Doc( name="Test" )
	outgoing.add( bo_in )
	
	# Get encoded PCOS data 	
	generated_data = outgoing.as_bytearray()

	reqf = open('data.pcos', 'w')
	reqf.write( generated_data )
	reqf.close()

	# Read back and test values
	incoming = Doc( generated_data )

	# jump to the block of interest
	bo_out = incoming.block( 'Bo' )

	assert bo_out.read_byte() == 44
	assert bo_out.read_bytes(len(rawbytes)) == rawbytes
	assert bo_out.read_bool() == False
	assert bo_out.read_bool() == True

	assert bo_out.read_uint() == 127
	assert bo_out.read_uint() == 128
	assert bo_out.read_int() == 63
	assert bo_out.read_int() == 64

	assert bo_out.read_ulong() == 127
	assert bo_out.read_ulong() == 128
	assert bo_out.read_long() == 63
	assert bo_out.read_long() == 64
	assert (abs(bo_out.read_double() - 3.14) < 0.001)

	assert bo_out.read_bytestr(len(rawbytes)) == rawbytes
	assert bo_out.read_string() == varstr


def _writing_test_pong():
	"""Test for PCOS Pong message"""

	tm = create_output_block( 'Tm' )
	tm.write_ulong( 1335795040 )

	msg = Doc( name="Po" )
	msg.add( tm )
	
	# Get encoded PCOS data 	
	generated_data = msg.as_bytearray()

	# Comparison data
	sample_data = binascii.unhexlify( '50434f530002506f0102546d0584fcfaba60' )
	
	assert str(generated_data) == str(sample_data)
	return generated_data


def _writing_test_error():
	"""Test for PCOS Error message"""

	bo = create_output_block( 'Bo' )
	bo.write_uint( 100 )
	bo.write_string( 'only a test' )

	msg = Doc( name="Er" )
	msg.add( bo )
	
	# Get encoded PCOS data 	
	generated_data = msg.as_bytearray()

	# Comparison data
	sample_data = binascii.unhexlify( '50434f53000245720102426f0d640b6f6e6c7920612074657374' )
	assert str(generated_data) == str(sample_data)


if __name__ == "__main__":
	"""Tests basic parser functionality."""

	# datatype serialization test
	_datatype_test()

	# Reading test...
	_reading_test_pong()
	
	# Writing test...
	_writing_test_pong()
	_writing_test_error()

	print "Looks good."

