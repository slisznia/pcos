PCOS - Portable Compact Object Serialization


Intro
=====

PCOS is a binary-encoding that is both language and platform neutral. It sup­ports basic types, such as integers, strings and bytes as well as user-defined types. User-defined types can be arbitrarily nested, forming even more complex structures.

PCOS borrows from other bin­ary inter­change for­mats, such that it’s schema-based. One major distinction is that PCOS is not "name-value pair" on the wire, making PCOS less extensible1, but very compact and efficient to parse.

Any participant of PushCoin, such as a mobile application or a point-of-sale system, must emit and consume PCOS-compatible messages. Information which follows outlines the rules for building such messages.

PCOS reference implementations for various languages can be found in project’s GitHub.

	* Fundamental types
	* User types and type aliases
	* Array
	* Optional fields
	* Message structure 


License
=======

Prtable, Compact Object Serialization is licensed under the Creative Commons Attribution 3.0 Unported License. 
http://creativecommons.org/licenses/by/3.0/


Fundamental Types
=================

PCOS serialization supports a range of primitive types. Each one is represented by a reserved keyword and has an associated storage and encoding attributes.  All PCOS messages, including those carrying user-defined types, can be broken down to a series of fields defined solely by these fundamental types:

|Name |Serialized Size |Range |Description|

|-----|----------------|------|-----------|

|byte |8-bits |0 to 255 |Typically used to store opaque binary data.|

|bool |8-bit |0 to 1 |Used for representing Boolean values of True (1) or False (0).|

|int |variable |—(2^31) to 2^31 — 1 |int32 ZigZag encoded signed varint. Varint encoding is described below.|

|uint |variable |0 to 2^32 — 1 |unsigned int32 varint.|

|long |variable |—(2^63) to 2^63 — 1 |int64 ZigZag encoded signed varint. Varint encoding is described below.|

|ulong |variable |0 to 2^64 — 1 |unsigned int64 varint.|

|double |64-bit |1.7E ± 308 (15 digits) |IEEE 754 floating point|

|string |variable ||uint followed by a UTF-8 encoded byte-string.|


Varint datatype
---------------	

Varints is a space-efficient encoding of integers. Each byte contains the next 7 big-endian bits of the integer. The low bits of the byte store number data with the high bit of the byte used to indicate if another byte is required.

More formally, bits 0 through 6 of the nth octet of the encoded data contain big-endian bits 7*(n-1) through 7*(n-1)+7 of the integer, and bit 7 of the nth octet is set if and only if octet nth+1 is present.

PCOS follows network-byte order for encoding integers. This is in contrast to Google Protobuffers.

PCOS encodes signed varint integers by mapping them to unsigned integers using a zig-zag scheme. Thus, numbers with a small absolute value consume small number of bytes on the wire with just a slight processing overhead.

To zig-zag encode an int32 number, one can apply the following transformation:

encode_zigzag32( n ):
        return (n << 1) ^ (n >> 31)

To zig-zag encode an int64 number:
encode_zigzag64( n ):
        return (n << 1) ^ (n >> 63)

Correspondingly, to unwrap a zig-zag number, we apply this transformation:

decode_zigzag( n ):
        return (n >> 1) ^ (-(n & 1))


Varint example
--------------

For example, a number 160 encoded as signed varint looks like so:

1000 0010 0100 0000

The application reads one byte at a time, starting with the first octet:
1000 0010
|___more data bit!

It then proceeds to read the second octet, which is also the last octet based on the MSB value:
0100 0000
|___last octet!

We remember that each following octet fills space of the previous octet shifted 7 bits to the left and having its MSB dropped. Putting it all together, we have:

# drop MSB of first octet
X000 0010

# shift first octet left 7 bits
0000 0001 0000 0000

# read second octet, drop MSB
X100 0000

# bitwise-OR the octets
      0000 0001 0000 0000
OR               100 0000
=     0000 0001 0100 0000 (320 decimal)

# un-zigzag
decode_zigzag( 320 ) == 160

The last step, un-zigzag, would be omitted if the field was declared unsigned varint (uint).


Type aliases and user-defined types
===================================

PCOS supports user-defined types and type aliases. With the type keyword we can assign alternative names to existing types or create composite, user-defined types.


Type aliases
------------

Type aliases are user-defined names for existing types. Providing a descriptive name for a type may clarify the purpose of the data, otherwise potentially confusing to the user. It also simplifies the process of changing the underlying data-type of a field, specially if the field exists in many definitions.

To create a type-alias, use the type keyword as follows:

type <new-type-name> : <existing type>;

Examples:

  type account_id : uint; // type alias of primitive type
  type member_id : account_id; // type alias of another alias
 

Changing the type of a field may break binary compatibility with existing applications which already rely on the previous type. Thus, type aliasing does not ensure backwards compatibility.


User-defined types
------------------

User-defined types are compositions of fields of primitive or user-defined types. There is no limit on nesting of user-defined types, but do keep in mind that accessing most inner value may be cumbersome.

To create a user-defined type, use the type keyword as follows:

type <new-type-name> { <semicolon-separated list of fields> };

Example:

  type address
  {
    street : string;  // variable-length character array
    city : string;  // variable-length character array
    zip : byte[5];  // fixed-length byte array
    state_code : string;  // variable-length character array
  };


Array
=====

An array is an ordered collection of values. The elements of an array can be of primitive type or user-defined type. All of the values in an array must be of the same type. The type of the array is the type of the values it holds, followed by the characters [].


Fixed-length array
------------------

An array with a constant size is called a fixed-length array. It’s declared as:

type <identifier> : <type-of-an-array>[<positive integer>];

For example:

  type checksum : byte[50];
  type names : string[7]; # array of 7 strings
  type factors : double[7];

The fixed-length array has no length indicator on the wire since number of elements is always constant (as defined in the schema).


Variable-length array
---------------------

An array that doesn’t define an upper limit on it’s element count is called variable-length array. It’s declared as:

type <identifier> : <type-of-an-array>[];

For example:

  type checksum : byte[];
  type names : string[];
  type factors : double[];
 

The variable-length array has the following properties:

 * It occupies a variable amount of storage, depending on the number of elements in the array.
 * The length indicator is always present on the wire indicating the number of elements in the array.
 * The datatype of the length indicator is uint, an unsigned varint. 


Optional fields
===============

Fields in a data-block can have optional specifier. When a field is marked as optional, it’s an indication that a value may not be present. When serialized, optional fields are prefixed with a bool field indicating if the data is present, followed by the data itself.

Below is an example of a "distance" field declared as optional. This field would occupy either 1 byte on the wire, if the value was not present, or 9 bytes if the value was present.

distance : double, optional;


Message Structure
=================

The PCOS message is made up of three types of segments:

* Message Header segment
* Data Block Enumeration segment
* Data Block segments (zero or more) 

Next, we go over each of the segments explaining their structure and purpose.


The Message Header segment
--------------------------

The PCOS Message Header is a data structure present in all PCOS messages. It has the following definition:

type message_header
{
  magic : byte[4], const="PCOS";
  flags: byte, const=0; # reserved for future use
  message_id : string;
};


The Data Block Enumeration segment
----------------------------------

Following the Message Header, starts the Data Block Enumeration segment. This segment enumerates all the data blocks — their identifiers and sizes — present in the message. Thanks to the enumeration segment, the parsing code can quickly locate blocks of interest, while skipping blocks it does not care about.

First, we define the data block-meta structure, which briefly describes a single data block:

type data_block_meta
{
  block_id : string;
  block_length : uint;
};

The Data Block Enumeration is defined as variable-length array of data_block_meta values:

type block_enumeration
{
  blocks : data_block_meta[];
};


The Data Block segment
----------------------

Finally we arrive at the last segment type of the PCOS message — the Data Block segment. All message data, such as transaction and account information, must reside in a data block.

A message may have zero or more data blocks and each data block may have a unique composition of user-defined or primitive types.

All PCOS messages have their data blocks defined in this SDK and are guaranteed to stay binary compatible in the foreseeable future.


Minimum PCOS message
--------------------

Putting all this together, let’s describe an absolute minimum PCOS message that we can encounter:

type pcos_message
{
  # Header (7 bytes):
  #  PCOS(4) + flags(1) + message_id(2) = 7
  header : message_header;

  # empty block enumeration (1 byte for variable-length array size)
  block_enum : block_enumeration;
};

Above tells us that the shortest PCOS message, one without any data blocks, is 8 bytes long. This is helpful as any payload shorter than that is simply not a valid PCOS message!


Recommended and maximum message lengths
---------------------------------------

The PCOS protocol attempts to minimize the serialized message footprint. The recommended guideline for the PCOS serialized message length is 1024 bytes or less. Messages above 4096 bytes in length may be considered a DoS attack and be discarded.
