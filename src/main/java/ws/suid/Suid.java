package ws.suid;

import java.io.IOException;

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
 * | 0000 0000 | 000b bbbb | bbbb bbbb | bbbb bbbb | bbbb bbbb | bbbb bbbb | bbbb bbii | iiii iiss |
 * |_______________________________________________|_______________________________________________|
 * 
 *   0 = 11 reserved bits
 *   b = 43 block bits
 *   i = 8 ID bits
 *   s = 2 shard bits
 * </pre>
 * 
 * <p>The first 11 bits are reserved and always set to {@code 0}. The next 43 bits are used for
 * the {@code block} number. These are handed out by a centralized server. Then there are 8 {@code ID}
 * bits which are to be filled in by the generator. The last 2 bits are reserved for the {@code shard}
 * ID. To prevent a single point of failure, at most 4 individual hosts can be handing out ID's for
 * a certain domain, each with their own {@code shard} ID.</p>
 * 
 * <p>To make the String representation of {@code Suid}s both short and easily human-readable and 
 *   write-able, an encoding scheme is used based on the alphanumerals [a-z,0-9] as follows:</p> 
 * 
 * <pre>
 *   0123456789a cdefghijk mn p rstuvwxyz  = 32 character alphabet
 *              ^         ^  ^ ^ 
 *              b         l  o q
 * 
 *   bloq = 4 Replacement symbols:
 *     b = 00
 *     l = 01
 *     o = 02
 *     q = 03
 * </pre>
 * 
 * <p>Using only lowercase (to make it easily writable by humans), the alphanumerals
 * give us 36 individual tokens in our alphabet. To make things simpler, we take out 
 * 4 characters and use them as replacement symbols instead: 'b', 'l', 'o' and 'q'.
 * Now we end up with a 32 token alphabet, neatly encoding 5 bits per token.</p>
 * 
 * <p>We can use the replacement symbols to perform some 'compression'. Using the fact 
 * that all blocks will end with the characters '00', '01', '02' or '03' (for shards
 * 0 .. 3) we can save one character off any block suid by replacing the character
 * sequence by it's corresponding replacement symbol. This at the same time uniquely
 * marks a suid as a block suid.</p>
 *  
 * @author Stijn de Witt [StijnDeWitt@hotmail.com]
 */
@JsonFormat(shape=JsonFormat.Shape.STRING)
@JsonDeserialize(using=Suid.Deserializer.class)
@JsonSerialize(using=Suid.Serializer.class)
public final class Suid extends Number implements CharSequence, Comparable<Suid> {
	private static final long serialVersionUID = 1L;

	/** Convenient constant for a Suid with a value of {@code 0L}. */
	public static final Suid NULL = new Suid(0L);
	
	/** Prefix used when serializing to/from JSON. */
	public static final String PREFIX = "Suid:";
	/** Serializes to JSON */
	public static final class Serializer extends StdSerializer<Suid> {
		public Serializer(){
			super(Suid.class);
		}
		@Override public void serialize(Suid value, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonGenerationException {
			jgen.writeString(value.toJSON());
		}
	}
	/** Deserializes from JSON */
	public static final class Deserializer extends StdDeserializer<Suid> {
		public Deserializer() {
			super(Suid.class);
		}
		@Override public Suid deserialize(JsonParser parser, DeserializationContext ctx) throws IOException, JsonProcessingException {
			return Suid.valueOfJSON(parser.getValueAsString());
		}
		
	}
	
	/** Mask that singles out the reserved bits */
	public static final long MASK_RESERVED =  0xffe0000000000000L;
	/** Mask that singles out the block bits */
	public static final long MASK_BLOCK =     0x001ffffffffffc00L;
	/** Mask that singles out the ID bits */
	public static final long MASK_ID =        0x00000000000003fcL;
	/** Mask that singles out the shard bits */
	public static final long MASK_SHARD =     0x0000000000000003L;
	/** Mask that singles out a token */
	public static final long MASK_TOKEN =     0x000000000000001fL;

	/** Number of reserved bits */
	public static final int COUNT_RESERVED =  11;
	/** Number of block bits */
	public static final int COUNT_BLOCK =     43;
	/** Number of ID bits */
	public static final int COUNT_ID =        8;
	/** Number of shard bits */
	public static final int COUNT_SHARD =     2;

	/** Offset of block bits within suid (from LSB) */
	public static final int OFFSET_BLOCK =    COUNT_ID + COUNT_SHARD;
	/** Offset of ID bits within suid (from LSB) */
	public static final int OFFSET_ID =       COUNT_SHARD;

	/** Alphabet used when converting suid to string */
	public static final String ALPHABET = "0123456789acdefghijkmnprstuvwxyz";
	/** Replacement symbols used when converting suid to string */
	public static final String[][] REPLACEMENT_SYMBOLS = {{"b", "00"},{"l", "01"},{"o", "02"},{"q", "03"}};
	/** Chars that are legal to use in a suid string (Alphabet + replacement symbols). */
	public static final String LEGAL_CHARS = ALPHABET + "bloq";

	private long value;
	private transient String strVal; // cache, should not be serialized

	/**
	 * Constructor needed by Jackson. Protected to signal to the JVM that this is a value class.
	 * 
	 * <p>Use the static {@code valueOf} methods to instantiate a suid.</p>
	 * 
	 * @param value The value of the new suid as a (possibly JSON) string, or {@code null}. 
	 */
	protected Suid(String value) {
		this(value == null ? 0L : Suid.valueOf(value.startsWith(PREFIX) ? value.substring(PREFIX.length()) : value).longValue());
	}

	/**
	 * Private constructor to signal to the JVM that this is a value class.
	 * 
	 * <p>Use the static {@code valueOf} methods to instantiate a suid.</p>
	 * 
	 * @param value The long value to set this Suid to.
	 *
	 * @see #valueOf(long) 
	 * @see #valueOf(long, int, byte)
	 * @see #valueOf(String)
	 * @see #valueOfBase32(String)
	 * @see <a href="https://docs.oracle.com/javase/8/docs/api/java/lang/doc-files/ValueBased.html">Java 8: Value Based</a>
	 * @see <a href="http://cr.openjdk.java.net/~jrose/values/values-0.html">CR OpenJDK: State of the Values</a>
	 */
	private Suid(long value) {
		this.value = value;
	}

	/**
	 * Creates a suid based on the given long {@code value}.
	 * 
	 * @param value The long value
	 * 
	 * @return A new suid, never {@code null}.
	 */
	public static Suid valueOf(long value) {
		return value == NULL.longValue() ? NULL : new Suid(value);
	}

	/**
	 * Creates a suid based on the given {@code block} number, generator {@code id} and {@code shard} id.
	 * 
	 * @param block The block number 
	 * @param id The generator ID bits
	 * @param shard The shard ID
	 * 
	 * @return A new suid, never {@code null}.
	 */
	public static Suid valueOf(long block, int id, byte shard) {
		return new Suid(~MASK_RESERVED & ( 
				(MASK_BLOCK & (block << OFFSET_BLOCK)) | 
				(MASK_ID    & (id    << OFFSET_ID))    | 
				(MASK_SHARD & shard)
			)
		);
	}

	/**
	 * Creates a suid based on the given {@code compressed} string.
	 * 
	 * @param compressed The compressed base32 string
	 * @return The new suid, or {@code null} if {@code compressed} was {@code null}.
	 * 
	 * @see #decompress
	 * @see #valueOfBase32
	 */
	public static Suid valueOf(String compressed) {
		if (compressed == null) return null;
		String base32 = decompress(compressed);
		return valueOfBase32(base32);
	}

	/**
	 * Creates a suid based on the given {@code base32} string.
	 * 
	 * @param base32 The base32 string
	 * @return The new suid, or {@code null} if {@code compressed} was {@code null}.
	 * 
	 * @see #decompress
	 */
	public static Suid valueOfBase32(String base32) {
		if (base32 == null) return null;
		long value = 0;
		for (int i=0; i<base32.length(); i++) {
			int idx = ALPHABET.indexOf(base32.charAt(i));
			if (idx == -1) throw new IllegalArgumentException("Unable to parse input to a SUID: " + base32);
			value = value * ALPHABET.length() + idx;
		}
		return new Suid(value);
	}

	/**
	 * Returns a compressed base-32 string representation of this suid.
	 * 
	 * @return A String of at least 1 character and at most 11 characters.
	 * 
	 * @see #compress
	 */
	@Override public String toString() {
		if (strVal == null) {
			strVal = compress(toBase32());
		}
		return strVal;
	}
	
	/**
	 * Returns a base-32 String representation of this suid that is not compressed.
	 * 
	 * <p>This method uses a 32 character {@code ALPHABET} to create the 
	 * base-32 representation of this suid.</p> 
	 * 
	 * @return A String of at least 1 character and at most 11 characters, never {@code null}.
	 * 
	 * @see #ALPHABET
	 */
	public String toBase32() {
		StringBuilder builder = new StringBuilder();
		for (int i=10; i>=0; i--) {
			int idx = (int) ((value >> (i * 5)) & MASK_TOKEN);
			if ((idx > 0) || (builder.length() > 0))
				builder.append(ALPHABET.charAt(idx));
		}
		if (builder.length() == 0)
			builder.append('0');
		return builder.toString();
	}
	
	/**
	 * Compresses the given {@code base32} string.
	 * 
	 * @param base32 The string to compress, never {@code null}.
	 * @return The compressed string, which may be shorter or of 
	 * 			equal length but never longer than the given String.
	 * 
	 * @see #decompress
	 */
	public static String compress(String base32) {
		String result = base32;
		for (String[] replacement : REPLACEMENT_SYMBOLS)
			result = result.replace(replacement[1], replacement[0]);
		return result;
	}

	/**
	 * Decompresses the given {@code compressed} string.
	 *
	 * @param compressed The string to decompress, never {@code null}.
	 * @return The decompressed string, which may be longer or of 
	 *			equal length but never shorter than the given String,
	 *			or {@code null} if {@code compressed} was {@code null}.
	 *
	 * @see #compress
	 */
	public static String decompress(String compressed) {
		if (compressed == null) return null;
		String result = compressed;
		for (String[] replacement : REPLACEMENT_SYMBOLS) {
			result = result.replace(replacement[0], replacement[1]);
		}
		return result;
	}

	@Override public int intValue() {
		return (int) value;
	}

	/**
	 * Returns this suid's underlying value.
	 */
	@Override public long longValue() {
		return value;
	}

	@Override public float floatValue() {
		return (float) value;
	}

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
	 * Gets the block number.
	 * 
	 * @return A long with just the block number.
	 */
	public long getBlock() {
		return (value & MASK_BLOCK) >> OFFSET_BLOCK;
	}

	/**
	 * Gets the shard ID.
	 * 
	 * @return A byte with the shard number (always in range {@code 0 .. 3}).
	 */
	public byte getShard() {
		return (byte) (value & MASK_SHARD);
	}

	/**
	 * Gets the generated ID.
	 * 
	 * @return An int with the generated ID (always in range {@code 0 .. 255}).
	 */
	public int getId() {
		return (int) (value & MASK_ID);
	}

	/**
	 * Indicates whether this Suid is a block suid.
	 * 
	 * <p>Block suids are the only suids a server will ever distribute. They have
	 * all zeroes in the ID bits. The ID generator on the client will fill in these
	 * bits to generate ID's. Block suids are themselves also valid IDs. The string
	 * representation of a block suid is always 'compressed', because block suids'
	 * normalized base-32 string representations always end with one of the character
	 * sequences {@code "01"}, {@code "02"}, {@code "03"} or {@code "04"}, which
	 * are replaced by the replacement symbols {@code 'b'}, {@code 'l'}, 
	 * {@code 'o'} and {@code 'q'} respectively.</p>
	 * 
	 * @return {@code true} if this suid is a block suid, {@code false} otherwise.
	 */
	public boolean isBlock() {
		return getId() == 0;
	}

	/**
	 * Returns the length of the compressed string representation of this suid.
	 * 
	 * <p>Equivalent to {@code toString().length()}.</p>
	 */
	@Override public int length() {
		return toString().length();
	}

	/**
	 * Returns the char present at the given {@code index} in the compressed string representation of this suid.
	 * 
	 * <p>Equivalent to {@code toString().charAt(index)}.</p>
	 */
	@Override public char charAt(int index) {
		return toString().charAt(index);
	}

	@Override public CharSequence subSequence(int start, int end) {
		return toString().subSequence(start, end);
	}

	@Override public int hashCode() {
		return Long.hashCode(value);
	}

	@Override public boolean equals(Object obj) {
		return obj != null && obj.getClass().equals(Suid.class) && ((Suid) obj).value == value;
	}

	@Override public int compareTo(Suid obj) {
		return Long.compare(value, obj.value);
	}

	/**
	 * Serializes this suid to JSON.
	 * 
	 * <p>This method returns a JSON string of the form {@code "Suid:xxxxx"} where 
	 * {@code xxxxx} is the suid's string representation. E.G: {@code "Suid:14shd"}.</p>
	 * 
	 * @return The JSON string, never {@code null}.
	 * 
	 * @see #PREFIX
	 * @see #valueOfJSON(String)
	 * @see Serializer
	 * @see Deserializer
	 */
	public String toJSON() {
		return PREFIX + toString();
	}

	/**
	 * Indicates whether the given {@code value} looks like a valid suid string.
	 * 
	 * <p>If this method returns {@code true}, this only indicates that it *might*
	 * be valid. There are no guarantees.</p>
	 * 
	 * @param value The value, may be {@code null}, in which case this method returns {@code false}.
	 * @return {@code true} if it looks valid, {@code false} otherwise.
	 * 
	 * @see #valueOf(String)
	 */
	public static boolean looksValid(String value) {
		if (value == null) {
			return false;
		}
		int len = value.length();
		if ((len == 0) || (len > 11)) {
			return false;
		}
		if ((len == 11) && (ALPHABET.indexOf(value.charAt(0)) > 8)) {
			return false;
		}
		for (int i=0; i<len; i++) {
			if (LEGAL_CHARS.indexOf(value.charAt(i)) == -1) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Indicates whether the given {@code json} string looks like a valid JSON representation of a suid.
	 * 
	 * <p>If this method returns {@code true}, this only indicates that the JSON *might* be valid as a suid. 
	 * There are no guarantees.</p>
	 * 
	 * @param json The json string, may be {@code null} in which case this method returns {@code false}.
	 * @return The suid, or {@code null}.
	 * 
	 * @see #looksValid
	 */
	public static boolean looksValidJSON(String json) {
		if ((json == null) || (json.length() == 0)) {
			return false;
		}
		if (! json.startsWith(PREFIX)) {
			return false;
		}
		return Suid.looksValid(json.substring(PREFIX.length()));
	}

	/**
	 * Deserializes the given JSON string into a suid, if possible.
	 * 
	 * <p>This method checks whether the given {@code json} string
	 * looks valid. If it does, it's deserialized into a suid and returned.
	 * If it does not look valid, {@code null} is returned instead.</p>
	 * 
	 * @return A suid, or {@code null}.
	 * 
	 * @see #looksValidJSON(String)
	 * @see #toJSON
	 * @see #PREFIX
	 * @see Deserializer
	 */
	public static Suid valueOfJSON(String json) {
		if (! Suid.looksValidJSON(json)) return null;
		return valueOf(json.substring(PREFIX.length()));
	}
}
