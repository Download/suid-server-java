package ws.suid;

import java.util.List;

import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.transaction.Transactional;

/**
 * Service that fetches suid ID blocks from the database.
 * 
 * <p>This service looks up the default datasource and fetches suid blocks from it.
 * There must be a table present named {@code `suid`} with columns {@code `block`} 
 * and {@code `shard`}. If records are present in this db, this service will generate
 * suid blocks using the same shard. If no records are present, this service will
 * insert the first record using {@code 0} as the shard id.</p>
 *  
 * @author Stijn de Witt [StijnDeWitt@hotmail.com]
 */
@Stateless
@LocalBean
public class SuidService {
	/** Max. number of suid blocks that may be requested in a single request. */
	public static final int MAX_REQUEST_BLOCKS = 8;
	
	// small singleton to maintain a server-side ID pool
	private static final class Generator {
		static Generator instance;
		Byte shard;
		Suid block;
		byte id;
		private Generator() {}
		public synchronized Suid next(SuidService suidService) {
			if (id >= Suid.IDSIZE) {id = 0;	block = null;}
			if (block == null) {
				block = suidService.nextBlocks(1)[0];
				shard = Byte.valueOf(block.getShard());
			}
			return new Suid(block.getBlock(), id++, block.getShard());
		}
		public static final Generator get() {
			return instance == null ? instance = new Generator() : instance;
		}
	}
	
	@PersistenceContext
	private EntityManager em;
	
	/**
	 * Gets the next {@code count} ID blocks.
	 * 
	 * <p>Parameter count must be in the range {@code 1 .. MAX_REQUEST_BLOCKS}. 
	 * If count is less than {@code 1}, it is set to {@code 1}. 
	 * If {@code count} is greater than {@code MAX_REQUEST_BLOCKS}, it is set to {@code MAX_REQUEST_BLOCKS}.</p>
	 * 
	 * @param count The number of blocks to get. Is forced to be in range {@code 1 .. 8}.
	 * @return An array of block {@code Suid}s.
	 * @see #MAX_REQUEST_BLOCKS
	 */
	@Transactional(Transactional.TxType.REQUIRES_NEW)
	@SuppressWarnings("unchecked")
	public Suid[] nextBlocks(int count) {
		if (count < 1) count = 1;
		if (count > MAX_REQUEST_BLOCKS) count = MAX_REQUEST_BLOCKS;
		int genBlock = 0;
		Byte shard = Generator.get().shard;
		if (shard == null) {
			genBlock = 1;
			Query query = em.createNamedQuery(SuidRecord.ALL, SuidRecord.class);
			query.setFirstResult(0).setMaxResults(1);
			List<SuidRecord> existing = (List<SuidRecord>) query.getResultList();
			if (!existing.isEmpty()) {shard = existing.get(0).getShard();}
			else {shard = Byte.valueOf((byte) 0);}
		} 
		SuidRecord[] blocks = new SuidRecord[count + genBlock];
		for (int i=0; i<count+genBlock; i++) {
			em.persist(blocks[i] = new SuidRecord(shard));
		}
		em.flush();
		Suid[] results = new Suid[count];
		for (int i=genBlock; i<count+genBlock; i++) {
			em.refresh(blocks[i]);
			results[i-genBlock] = new Suid(blocks[i].getBlock().longValue(), (byte) 0, shard);
		}
		if (genBlock == 1) {
			em.refresh(blocks[0]);
			Generator.get().block = new Suid(blocks[0].getBlock().longValue(), (byte) 0, shard);
			Generator.get().shard = shard;
		}
		// Aggressively delete all suid records with lower block value than last block.
		// We aim for just a single record in the db at any time, though multiple
		// records may be present for short periods due to concurrency.
		Query query = em.createNamedQuery(SuidRecord.DEL);
		query.setParameter("block", blocks[count+genBlock-1].getBlock());
		query.executeUpdate();
		em.flush();
		return results;
	}

	/** 
	 * Fetches a single suid.
	 * 
	 * <p>This method fetches a single Suid from a server-side pool of one block.
	 * As such, one in {@code Suid.IDSIZE} calls to this method will result in 
	 * queries to the database to fetch the next block.</p>
	 * 
	 * @return The next unique Suid, never {@code null}.
	 * 
	 * @see Suid#IDSIZE
	 */
	public Suid next() {
		return Generator.get().next(this);
	}
}
