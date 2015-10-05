package ws.suid;

import java.math.BigInteger;

import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.transaction.Transactional;

/**
 * Service that fetches suid ID blocks from the database.
 * 
 * <p>This service looks up the datasource named {@code SuidRWDS} and 
 * fetches suid blocks from it.</p>
 * 
 * <p><b>NOTE</b>: Currently, only MySql is supported.</p>
 * 
 * @author Stijn de Witt [StijnDeWitt@hotmail.com]
 */
@Stateless
@LocalBean
public class SuidService {
	// small singleton to maintain a server-side ID pool
	private static final class Generator {
		private static Generator instance;
		private Suid block;
		private int id;
		private Generator() {}
		public synchronized Suid next(SuidService suidService) {
			if (id >= Suid.IDSIZE) {
				id = 0;
				block = null;
			}
			if (block == null) {
				block = suidService.nextBlocks(1)[0]; 
			}
			return Suid.valueOf(block.getBlock(), id++, block.getShard());
		}
		public static final Generator get() {
			if (instance == null) instance = new Generator();
			return instance;
		}
	}
	
	public static final int MAX_REQUEST_BLOCKS = 8;
	
	@PersistenceContext(unitName = "SuidRWPU")
	private EntityManager em;
	
	/**
	 * Gets the next {@code count} ID blocks.
	 * 
	 * <p>Parameter count must be in the range {@code 1 .. 8}. If count is less than
	 * {@code 1}, it is set to {@code 1}. If {@code count} is greater than 8, it is
	 * set to {@code 8}.</p>
	 * 
	 * @param count The number of blocks to get. Is forced to be in range {@code 1 .. 8}.
	 * @return An array of block {@code Suid}s.
	 */
	@Transactional(Transactional.TxType.REQUIRES_NEW)
	public Suid[] nextBlocks(int count) {
		if (count < 1) count = 1;
		if (count > MAX_REQUEST_BLOCKS) count = MAX_REQUEST_BLOCKS;
		Suid[] results = new Suid[count];
		for (int i=0; i<count; i++)
			results[i] = nextBlock();
		return results;
	}
	
	public Suid next() {
		return Generator.get().next(this);
	}

	private Suid nextBlock() {
		Query query = em.createNativeQuery("REPLACE INTO suid (shard) SELECT shard FROM suid;");
		query.executeUpdate();
		query = em.createNativeQuery("SELECT LAST_INSERT_ID() AS block, shard FROM suid;");
		Object[] rs = (Object[]) query.getSingleResult();
		Long block = Long.valueOf(((BigInteger) rs[0]).longValue());
		Byte shard = (Byte) rs[1]; 
		return Suid.valueOf(block, (byte) 0, shard);
	}
}
