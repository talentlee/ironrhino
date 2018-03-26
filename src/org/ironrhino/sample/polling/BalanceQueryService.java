package org.ironrhino.sample.polling;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;
import org.ironrhino.core.metadata.Profiles;
import org.ironrhino.core.service.EntityManager;
import org.ironrhino.core.throttle.Mutex;
import org.ironrhino.core.util.ExceptionUtils;
import org.ironrhino.core.util.NumberUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile(Profiles.DUAL)
public class BalanceQueryService {

	private final static String key = "balance_query";

	@Autowired
	private Logger logger;

	@Autowired
	private EntityManager<BalanceQuery> entityManager;

	@Autowired
	private StringRedisTemplate stringRedisTemplate;

	@Value("${" + AvailableSettings.STATEMENT_BATCH_SIZE + ":50}")
	private int batchSize = 50;

	@Value("${balance.query.threads:2}")
	private int threads = 2;

	private ThreadPoolExecutor threadPoolExecutor;

	@Value("${balance.query.maxAttempts:3}")
	private int maxAttempts = 3; // 最多尝试次数, 第一次+重试次数

	@Value("${balance.query.intervalFactor:120}")
	private int intervalFactor = 120; // 2分钟

	@PostConstruct
	private void init() {
		threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(threads);
	}

	@PreDestroy
	private void destroy() {
		threadPoolExecutor.shutdownNow();
	}

	@Transactional
	public void initData(int size) {
		String prefix = String.valueOf(System.currentTimeMillis());
		entityManager.execute(session -> {
			for (int i = 0; i < size; i++) {
				String accountNo = prefix + NumberUtils.format(i, 5);
				BalanceQuery bq = new BalanceQuery();
				bq.setAccountNo(accountNo);
				session.save(bq);
				if ((i + 1) % batchSize == 0) {
					session.flush(); // 触发jdbc的statement.executeBatch()提交
					session.clear(); // 清理session一级缓存防止大数据量下内存溢出
				}
			}
			return null;
		});
	}

	public long getQueueDepth() {
		return stringRedisTemplate.opsForSet().size(key);
	}

	@Mutex // 多实例情况下只有一个实例执行, 防止操作重复的数据
	@Scheduled(fixedRate = 10000)
	public void enqueue() {
		// 将未处理的放入队列, 并且修改状态
		entityManager.setEntityClass(BalanceQuery.class);
		DetachedCriteria dc = entityManager.detachedCriteria();
		dc.add(Restrictions.eq("status", BalanceQueryStatus.INITIALIZED));
		entityManager.iterate(batchSize, (entities, session) -> {
			for (BalanceQuery bq : entities) {
				bq.setStatus(BalanceQueryStatus.PROCESSING);
				bq.setModifyDate(new Date());
				session.update(bq);
			}
		}, entities -> {
			// 数据库事务提交之后再放入redis队列, 防止状态修改还没生效就被处理
			for (BalanceQuery bq : entities) {
				stringRedisTemplate.opsForSet().add(key, bq.getId());
				logger.info("enqueue {}#{}", bq.getId(), bq.getAccountNo());
			}
		}, dc);

		// 将处于处理中太久的任务重新放入队列, 这种情况是从redis队列中取出来了但是没有得到处理(比如强杀进程)
		// redis的set是去重的所以不会有重复
		// 查询操作是幂等的, 重复做没多大影响, 如果是数据更新并且对方不支持幂等, 重提需要谨慎对待
		dc = entityManager.detachedCriteria();
		dc.add(Restrictions.eq("status", BalanceQueryStatus.PROCESSING));
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.MINUTE, -10); // 十分钟前还没处理的
		dc.add(Restrictions.lt("modifyDate", cal.getTime()));
		entityManager.iterate(10, (entities, session) -> {
			for (BalanceQuery bq : entities) {
				stringRedisTemplate.opsForSet().add(key, bq.getId());
				logger.info("enqueue {}#{} repeatedly", bq.getId(), bq.getAccountNo());
			}
		}, dc);

		// 将可以重试的查询重新放入队列, 重试间隔时间和第几次相关
		for (int attempts = 1; attempts < maxAttempts; attempts++) {
			dc = entityManager.detachedCriteria();
			dc.add(Restrictions.eq("status", BalanceQueryStatus.TEMPORARY_ERROR));
			dc.add(Restrictions.eq("attempts", attempts));
			cal = Calendar.getInstance();
			cal.add(Calendar.SECOND, -intervalFactor * attempts); // 根据次数来决定间隔时长
			dc.add(Restrictions.lt("modifyDate", cal.getTime()));
			entityManager.iterate(10, (entities, session) -> {
				for (BalanceQuery bq : entities) {
					stringRedisTemplate.opsForSet().add(key, bq.getId());
					logger.info("enqueue {}#{} retried", bq.getId(), bq.getAccountNo());
				}
			}, dc);
		}
	}

	@Scheduled(fixedDelay = 5000)
	public void dequeue() {
		if (threadPoolExecutor.isShutdown())
			return;
		for (int i = 0; i < threads - threadPoolExecutor.getActiveCount(); i++) {
			threadPoolExecutor.submit(this::process);
		}
	}

	protected void process() {
		entityManager.setEntityClass(BalanceQuery.class);
		while (true) {
			String id = stringRedisTemplate.opsForSet().pop(key);
			if (id == null)
				break;
			BalanceQuery bq = entityManager.get(id);
			if (bq == null) {
				logger.warn("not found: {}", id);
				continue;
			}
			logger.info("dequeue {}#{}", bq.getId(), bq.getAccountNo());
			try {
				int result = entityManager.executeUpdate(
						"update BalanceQuery t set t.balance=?3,t.status=?4,t.modifyDate=?5,t.errorInfo=null,t.attempts=t.attempts+1 where t.id=?1 and t.status=?2",
						bq.getId(), bq.getStatus(), queryBalance(bq.getAccountNo()), BalanceQueryStatus.SUCCESSFUL,
						new Date());
				if (result == 1)
					logger.info("process {}#{} successful", bq.getId(), bq.getAccountNo());
				else
					logger.warn("process {}#{} successful and ignored", bq.getId(), bq.getAccountNo());
			} catch (Exception e) {
				boolean retryable = isTemporaryError(e);
				if (retryable) {
					int result = entityManager.executeUpdate(
							"update BalanceQuery t set t.errorInfo=?3,t.status=?4,t.modifyDate=?5,t.attempts=t.attempts+1 where t.id=?1 and t.status=?2",
							bq.getId(), bq.getStatus(), ExceptionUtils.getDetailMessage(e),
							(bq.getAttempts() + 1) == maxAttempts ? BalanceQueryStatus.FAILED
									: BalanceQueryStatus.TEMPORARY_ERROR,
							new Date());
					if (result == 1)
						logger.info("process {}#{} failed", bq.getId(), bq.getAccountNo());
					else
						logger.warn("process {}#{} failed and ignored", bq.getId(), bq.getAccountNo());
				} else {
					int result = entityManager.executeUpdate(
							"update BalanceQuery t set t.errorInfo=?3,t.status=?4,t.modifyDate=?5 where t.id=?1 and t.status=?2",
							bq.getId(), bq.getStatus(), ExceptionUtils.getDetailMessage(e), BalanceQueryStatus.FAILED,
							new Date());
					if (result == 1)
						logger.info("process {}#{} failed", bq.getId(), bq.getAccountNo());
					else
						logger.warn("process {}#{} failed and ignored", bq.getId(), bq.getAccountNo());
				}
			}
		}
	}

	protected BigDecimal queryBalance(String accountNo) throws IOException {
		// 模拟查询
		Random random = new Random();
		if (accountNo.length() < 5)
			throw new IllegalArgumentException("Illegal accountNo: " + accountNo);
		if (accountNo.endsWith("60"))
			throw new IOException("I/O Error");
		try {
			Thread.sleep(random.nextInt(10) * 100);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return new BigDecimal(random.nextInt(10000));
	}

	protected boolean isTemporaryError(Exception e) {
		return e instanceof IOException || e.getCause() instanceof IOException;
	}

}
