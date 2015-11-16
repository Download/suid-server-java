package ws.suid;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

/**
 * Stores a 53-bit service-unique ID in a 64-bit long.
 * 
 * <p>The bits are distributed over the 64-bit long as depicted below:</p>
 * 
 * <pre>
 *                     HIGH INT                                         LOW INT
 * ________________________________________________________________________________________________
 * |                                               |                                               |
 * | 0000 0000 | 000b bbbb | bbbb bbbb | bbbb bbbb | bbbb bbbb | bbbb bbbb | bbbb bbbb | biii iiis |
 * |_______________________________________________|_______________________________________________|
 * 
 *   0 = 11 reserved bits
 *   b = 46 block bits
 *   i = 6 ID bits
 *   s = 1 shard bit
 * </pre>
 * 
 * <p>The first 11 bits are reserved and always set to {@code 0}. The next 46 bits are used for
 * the {@code block} number. These are handed out by a centralized server. Then there are 6 {@code ID}
 * bits which are to be filled in by the generator. The last bit is reserved for the {@code shard}
 * ID. To prevent a single point of failure, two separate hosts can be handing out ID's for a certain 
 * domain, each with their own {@code shard} ID (0 or 1).</p>
 * 
 * <p>To make the String representation of {@code Suid}s both short and easily human-readable and 
 *   write-able, base-36 encoding is used. Using only lowercase makes suids easy for humans to read, 
 *   write and pronounce.</p>
 *  
 * @author Stijn de Witt [StijnDeWitt@hotmail.com]
 */
@JsonFormat(shape=JsonFormat.Shape.STRING)
@JsonDeserialize(using=Suid.Deserializer.class)
@JsonSerialize(using=Suid.Serializer.class)
public final class Suid extends Number implements CharSequence, Comparable<Suid> {
	private static final long serialVersionUID = 1L;
	
	/** Alphabet used when converting suid to string */
	public static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz";
	/** Mask that singles out the reserved bits */
	public static final long MASK_RESERVED =  0xffe0000000000000L;
	/** Mask that singles out the block bits */
	public static final long MASK_BLOCK =     0x001fffffffffff80L;
	/** Mask that singles out the ID bits */
	public static final long MASK_ID =        0x000000000000007eL;
	/** Mask that singles out the shard bits */
	public static final long MASK_SHARD =     0x0000000000000001L;
	/** Number of reserved bits */
	public static final byte COUNT_RESERVED = 11;
	/** Number of block bits */
	public static final byte COUNT_BLOCK =    46;
	/** Number of ID bits */
	public static final byte COUNT_ID =       6;
	/** Number of shard bits */
	public static final byte COUNT_SHARD =    1;
	/** Offset of reserved bits within suid (from LSB) */
	public static final byte OFFSET_RESERVED = COUNT_BLOCK + COUNT_ID + COUNT_SHARD;
	/** Offset of block bits within suid (from LSB) */
	public static final byte OFFSET_BLOCK =    COUNT_ID + COUNT_SHARD;
	/** Offset of ID bits within suid (from LSB) */
	public static final byte OFFSET_ID =       COUNT_SHARD;
	/** Offset of shard bit within suid (from LSB) */
	public static final byte OFFSET_SHARD =    0;
	/** The number of blocks available. */
	public static final long BLOCK_SIZE =      1 << COUNT_BLOCK;
	/** The number of IDs available in each block. */
	public static final long IDSIZE =          1 << COUNT_ID;
	/** The number of shards available */
	public static final long SHARDSIZE =       1 << COUNT_SHARD;

	/** Converts Suid to/from it's database representation. */ 
	@Converter(autoApply=true)
	public static class SuidConverter implements AttributeConverter<Suid, Long> {
		@Override public Long convertToDatabaseColumn(Suid suid) {
			return suid == null ? null : suid.toLong();
		}
		@Override public Suid convertToEntityAttribute(Long suid) {
			return suid == null ? null : new Suid(suid);
		}
	}
	
	/** Serializes to JSON */
	@SuppressWarnings("serial")
	public static final class Serializer extends StdSerializer<Suid> {
		public Serializer(){super(Suid.class);}
		@Override public void serialize(Suid value, JsonGenerator generator, SerializerProvider provider) throws IOException, JsonGenerationException {
			generator.writeString(value.toString());
		}
	}
	/** Deserializes from JSON */
	@SuppressWarnings("serial")
	public static final class Deserializer extends StdDeserializer<Suid> {
		public Deserializer() {super(Suid.class);}
		@Override public Suid deserialize(JsonParser parser, DeserializationContext ctx) throws IOException, JsonProcessingException {
			return new Suid(parser.getValueAsString());
		}
		
	}

	private long value;
	private transient String strVal; // cache, should not be serialized

	/**
	 * Creates a suid based on the given string {@code value}.
	 * 
	 * @param value The value of the new suid as a base-36 string, or {@code null}.
	 * 
	 * @throws NumberFormatException When the supplied value can not be parsed as base-36.
	 */
	public Suid(String value) throws NumberFormatException {
		this(value == null ? 0L : Long.parseLong(value, 36));
	}

	/**
	 * Creates a suid based on the given long {@code value}.
	 * 
	 * @param value The long value to set this Suid to.
	 */
	public Suid(long value) {
		this.value = value;
	}

	/**
	 * Creates a suid based on the given {@code block}, {@code id} and {@code shard} constituent parts.
	 * 
	 * @param block The block bits for the suid, in a long.
	 * @param id The id bits for the suid, in a byte.
	 * @param shard The shard bit for the suid, in a byte.
	 */
	public Suid(long block, byte id, byte shard) {
		this.value = ~MASK_RESERVED & ( 
			(MASK_BLOCK & (block << OFFSET_BLOCK)) | 
			(MASK_ID    & (id    << OFFSET_ID))    | 
			(MASK_SHARD & (shard << OFFSET_SHARD))
		);
	}

	/**
	 * Returns a base-36 string representation of this suid.
	 * 
	 * @return A String of at least 1 character and at most 11 characters.
	 */
	@Override public String toString() {
		if (strVal == null) {strVal = Long.toString(value, 36);}
		return strVal;
	}
	
	/**
	 * Use {@link #longValue()} instead.
	 */
	@Override public int intValue() {
		return (int) value;
	}

	/**
	 * Returns this suid's underlying value.
	 * 
	 * <p>Suid's use a {@code long} as underlying value. Avoid using
	 * {@code intValue} and {@code floatValue} as these perform
	 * narrowing conversions. These methods are mainly there 
	 * to satisfy the {@code Number} interface.</p>
	 */
	@Override public long longValue() {
		return value;
	}

	/**
	 * Use {@link #longValue()} instead.
	 * 
	 * <p>If you must have a floating point number, use {@code doubleValue} which can 
	 * actually store all possible suids (they are limited to 53 bits for this purpose).</p>
	 */
	@Override public float floatValue() {
		return (float) value;
	}

	/**
	 * Returns the value of this Suid as a double.
	 * 
	 * <p>Although suids internally use {@code long}s to store the bits, since they
	 * are limited to 53 bits, they can actually be represented as {@code double} as
	 * well without loss of precision.</p>
	 */
	@Override public double doubleValue() {
		return (double) value;
	}

	/**
	 * Converts this Suid to a Java Long.
	 * 
	 * @return This suid's value converted to a Long, never {@code null}.
	 */
	public Long toLong() {
		return Long.valueOf(value);
	}
	
	/**
	 * Gets the block bits.
	 * 
	 * @return A long with the block bits.
	 */
	public long getBlock() {
		return (value & MASK_BLOCK) >> OFFSET_BLOCK;
	}

	/**
	 * Gets the ID bits.
	 * 
	 * @return An int with the ID bits (always in range {@code 0 .. 63}).
	 */
	public byte getId() {
		return (byte) ((value & MASK_ID) >> OFFSET_ID);
	}

	/**
	 * Gets the shard bits.
	 * 
	 * @return A byte with the shard number (always in range {@code 0 .. 1}).
	 */
	public byte getShard() {
		return (byte) ((value & MASK_SHARD) >> OFFSET_SHARD);
	}

	/**
	 * Returns the length of the string representation of this suid.
	 * 
	 * <p>Equivalent to {@code toString().length()}.</p>
	 */
	@Override public int length() {
		return toString().length();
	}

	/**
	 * Returns the char present at the given {@code index} in the base-36 string representation of this suid.
	 * 
	 * <p>Equivalent to {@code toString().charAt(index)}.</p>
	 */
	@Override public char charAt(int index) {
		return toString().charAt(index);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override public CharSequence subSequence(int start, int end) {
		return toString().subSequence(start, end);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override public int hashCode() {
		return Long.hashCode(value);
	}

	/**
	 * Compares this suid to {@code that} for equality.
	 * 
	 * @param that The suid to compare to, may be {@code null}, in which case {@code false} will be returned.
	 * @return {@code true} if this suid is equals to {@code that}, {@code false} otherwise.
	 */
	@Override public boolean equals(Object that) {
		return that != null && that.getClass().equals(Suid.class) && ((Suid) that).value == value;
	}

	/**
	 * Compares this suid to {@code that} for order.
	 * 
	 * @param that The suid to compare to, never {@code null}.
	 * @return {@code -1} if this suid is less than, {@code 0} if it is equals to, and {@code 1} if it is greater than {@code that}.
	 * @throws NullPointerException when {@code that} is {@code null}.
	 */
	@Override public int compareTo(Suid that) throws NullPointerException {
		return Long.compare(value, that.value);
	}

	/**
	 * Indicates whether the given {@code value} looks like a valid suid string.
	 * 
	 * <p>If this method returns {@code true}, this only indicates that it's *probably*
	 * valid. There are no guarantees.</p>
	 * 
	 * @param value The value, may be {@code null}, in which case this method returns {@code false}.
	 * @return {@code true} if it looks valid, {@code false} otherwise.
	 */
	public static boolean looksValid(String value) {
		if (value == null) {return false;}
		int len = value.length();
		if ((len == 0) || (len > 11)) {return false;}
		if ((len == 11) && (ALPHABET.indexOf(value.charAt(0)) > 2)) {return false;}
		for (int i=0; i<len; i++) {
			if (ALPHABET.indexOf(value.charAt(i)) == -1) {return false;}
		}
		return true;
	}

	/**
	 * Converts the given list of {@code ids} to a list of longs.
	 * 
	 * @param ids The ids to convert, may be empty but not {@code null}.
	 * @return The list of longs, may be empty but never {@code null}.
	 */
	public static List<Long> toLong(List<Suid> ids) {
		List<Long> results = new ArrayList<Long>();
		for (Suid id : ids) {results.add(id.toLong());}
		return results;
	}
	
	/**
	 * Converts the given list of {@code ids} to a list of Suids.
	 * 
	 * @param ids The ids to convert, may be empty but not {@code null}.
	 * @return The list of Suids, may be empty but never {@code null}.
	 */
	public static List<Suid> valueOf(List<Long> ids) {
		List<Suid> results = new ArrayList<Suid>();
		for (Long id : ids) {results.add(new Suid(id.longValue()));}
		return results;
	}
}
