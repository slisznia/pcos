
# Portable, Compact Object Serialization (PCOS)

MIME type name: "application/vnd.pcos"

PCOS is a binary-encoding originally created at PushCoin for inter-application messaging. The serialization is both language and platform neutral. It supports basic types, such as integers, strings and bytes as well as user-defined compound types. User-defined types can nest, forming even more complex types.

PCOS borrows from other binary interchange formats, such that it's schema-based. One major distinction is that PCOS is not a "name-value pair" on the wire, making PCOS very compact and efficient to parse.

PCOS reference implementations for various languages can be found in this repository.

Information which follows outlines the rules for building PCOS messages.

## License

__Portable Compact Object Serialization__ is licensed under the Creative Commons Attribution 3.0 Unported License.

### You are free to...

* Share - copy and redistribute the material in any medium or format
* Adapt - remix, transform, and build upon the material for any purpose, even commercially.
* The licensor cannot revoke these freedoms as long as you follow the license terms.

### Attribution

You must give appropriate credit, provide a link to the license, and indicate if changes were made. You may do so in any reasonable manner, but not in any way that suggests the licensor endorses you or your use. 

For full license terms visit http://creativecommons.org/licenses/by/3.0/

## Table of Contents

1. Primitive types
2. Type aliases and user-defined types
3. Arrays
4. Optional fields
5. Message structure 

## Primitive types

PCOS serialization supports a range of primitive types. Each one is represented by a reserved keyword and has an associated storage and encoding attributes.  All PCOS messages, including those carrying user-defined types, can be broken down to a series of fields defined solely by these fundamental types:

<table>
<tr> <th>Name</th><th>Serialized Size</th><th>Range</th><th>Description</th> </tr>
<tr> <td>byte</td><td>8 bits </td><td>0 to 255 </td><td>Typically used to store opaque binary data.</td> </tr>
<tr> <td>bool </td><td>8 bits </td><td>0 to 1 </td><td>Used for representing Boolean values of True (1) or False (0).</td> </tr>
<tr> <td>int </td><td>variable </td><td>—(2^31) to 2^31 — 1 </td><td>int32 ZigZag encoded signed varint. Varint encoding is described below.</td> </tr>
<tr> <td>uint </td><td>variable </td><td>0 to 2^32 — 1 </td><td>unsigned int32 varint.</td> </tr>
<tr> <td>long </td><td>variable </td><td>—(2^63) to 2^63 — 1 </td><td>int64 ZigZag encoded signed varint. Varint encoding is described below.</td> </tr>
<tr> <td>ulong </td><td>variable </td><td>0 to 2^64 — 1 </td><td>unsigned int64 varint.</td> </tr>
<tr> <td>double </td><td>64 bits </td><td>1.7E ± 308 (15 digits) </td><td>IEEE 754 floating point</td> </tr>
<tr> <td>string </td><td>variable </td><td></td><td>uint followed by a UTF-8 encoded byte-string.</td> </tr>
</table>


### Varint datatype

Varint is a space-efficient encoding of integers. Each byte contains the next 7 big-endian bits of the integer. The low bits of the byte store number data with the high bit of the byte used to indicate if another byte is required.

More formally, bits 0 through 6 of the nth octet of the encoded data contain big-endian bits 7*(n-1) through 7*(n-1)+7 of the integer, and bit 7 of the nth octet is set if and only if octet nth+1 is present.

PCOS follows network-byte order for encoding integers. This is in contrast to Google Protobuffers.

PCOS encodes signed varint integers by mapping them to unsigned integers using a zig-zag scheme. Thus, numbers with a small absolute value consume small number of bytes on the wire with just a slight processing overhead.

To zig-zag encode an int32 number, one can apply the following transformation:

```
encode_zigzag32( n ):
        return (n << 1) ^ (n >> 31)
```

To zig-zag encode an int64 number:

```
encode_zigzag64( n ):
        return (n << 1) ^ (n >> 63)
```

Correspondingly, to unwrap a zig-zag number, we apply this transformation:

```
decode_zigzag( n ):
        return (n >> 1) ^ (-(n & 1))
```


### Varint example

Let's have look at the encoding of a number 160 as a signed varint:

```
1000 0010 0100 0000

The application reads one byte at a time, starting with the first octet:
1000 0010
|___more data bit!

It then proceeds to read the second octet, which is also the last octet based
on the MSB value:

0100 0000
|___last octet!

We remember that each following octet fills space of the previous octet shifted
7 bits to the left and having its MSB dropped. Putting it all together, we
have:

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

The last step, un-zigzag, would be omitted if the field was declared unsigned
varint (uint).
```

## Type aliases and user-defined types

PCOS supports user-defined types and type aliases. With the keyword `type` we can assign alternative names to existing types or create composite (user-defined) types.

### Type aliases

Type aliases are alternative names for existing primitive or user-defined types. Providing a descriptive name for a type often helps to inform of its intended purpose. It can also simplify the process of changing the underlying data-type for many fields sharing the same alias.

To create a type-alias, use the type keyword as follows:

```
type <new-type-name> : <existing type>;
```

Examples:

```
type account_id : uint; // type alias of primitive type
type member_id : account_id; // type alias of another alias
```

**Careful: changing the type of a field may break binary compatibility with existing applications which already rely on the previous type.**

## Compound types

Compund types are compositions of primitive or other user-defined compound types. There is no limit on nesting of compound types.

To create a compound type, use the type keyword as follows:

```
type <new-type-name> { <semicolon-separated list of fields> };
```

Example:

```
  type address
  {
    street : string;  // variable-length character array
    city : string;  // variable-length character array
    zip : byte[5];  // fixed-length byte array
    state_code : string;  // variable-length character array
  };
```

## Arrays

An array is an ordered collection of values. Elements of an array are of primitive or compound type. All values stored in the array are of the same type. The type of the array is the type of the values it holds.

### Fixed-length array

An array with a constant size is called a **fixed-length array**. It's declared as:

```
type <identifier> : <type-of-an-array>[<positive integer>];
```

For example:

```
type checksum : byte[50];
type names : string[7]; // array of 7 strings
type factors : double[7];
```

A fixed-length array has no length indicator on the wire since number of elements is always constant and specified in the schema.

### Variable-length array

An array that doesn't define an upper limit on it's element count is called **variable-length array**. It's declared as:

```
type <identifier> : <type-of-an-array>[];
```

For example:

```
type checksum : byte[];
type names : string[];
type factors : double[];
```
 
The variable-length array has the following properties:

* It occupies a variable amount of storage, depending on the number of elements in the array.
* The length indicator is always present on the wire and tells the receiving end about the number of elements in the array.
* The datatype of the length indicator is uint, an unsigned varint. 

## Optional fields

Fields can have an **optional** specifier. When a field is marked as optional, it's an indication that a value may not be present on the wire. When serialized, optional fields are prefixed with a bool-field indicating if the data is present, followed by the data itself.

Below is an example of a `distance` field declared optional. This field would occupy either 1 byte on the wire, if the value was not present, or 9 bytes if the value was present (double always consumes 8 bytes on the wire).

```
distance : double, optional;
```

## Message Structure

A PCOS message is made of __segments__. The first two segments are mandatory and the last segment is optional:

1. __Message Header__ segment.
2. __Data-Segment Enumeration__ segment.
3. __Data__ segments (zero or more).

Next, we go over each of the segments explaining its structure and purpose.

### The "Message Header" segment

The __Message Header__ segment identifies a PCOS message on the wire. It is a mandatory segment and is defined as follows:

```
type message_header
{
  magic : byte[4], const="PCOS";
  flags: byte, const=0; # reserved for future use
  message_id : string;
};
```

### The "Data-Segment Enumeration" segment

Following the __Message Header__, starts the mandatory __Data-Segment Enumeration__ segment. This segment enumerates data-carrying segments  — their identifiers and sizes — present in the message. Thanks to the enumeration segment, the receiving side can quickly locate segments of interest, while skipping over segments it does not care about without any overhead of parsing.

First, we define the **Data-Segment Meta** structure, which describes a single Data-Segment:

```
type data_segment_meta
{
  segment_id : string;
  segment_length : uint;
};
```

The Data-Segment Enumeration segment is defined as a variable-length array of data_segment_meta values:

```
type data_segment_enumeration
{
  segments : data_segment_meta[];
};
```

### The "Data-Segment" segment

At last, we arrive at the optional segment of any PCOS message — the __Data-Segment__. All user-provided message data, such as transaction record, account information or a PDF document, are put into the Data-Segments.

A PCOS message may have zero or more Data-Segments and each segment is made of user-defined or primitive types.

### Minimum PCOS message

Putting all this together, let's describe an absolute minimum PCOS message that we can encounter:

```
type pcos_message
{
  // Header (7 bytes): PCOS(4) + flags(1) + message_id(2) = 7
  header : message_header;

  # empty data-segment enumeration (1 byte for variable-length array size)
  segments: data_segment_enumeration;
};
```

From above, we read that the shortest PCOS message, one without any data-segments, is 8-bytes long. This is helpful as any payload shorter than that is simply not a valid PCOS message and can be immediately discarded.

## References

1. MIME type application - http://www.iana.org/assignments/media-types/application/vnd.pcos
2. Google Protobuffers - https://developers.google.com/protocol-buffers/

------------
The End

