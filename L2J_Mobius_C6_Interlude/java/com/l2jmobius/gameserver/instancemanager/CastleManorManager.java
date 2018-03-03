/*
 * This file is part of the L2J Mobius project.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jmobius.gameserver.instancemanager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.logging.Logger;

import com.l2jmobius.Config;
import com.l2jmobius.commons.concurrent.ThreadPoolManager;
import com.l2jmobius.commons.database.DatabaseFactory;
import com.l2jmobius.commons.util.Rnd;
import com.l2jmobius.gameserver.datatables.sql.ClanTable;
import com.l2jmobius.gameserver.model.ClanWarehouse;
import com.l2jmobius.gameserver.model.ItemContainer;
import com.l2jmobius.gameserver.model.L2Clan;
import com.l2jmobius.gameserver.model.L2Manor;
import com.l2jmobius.gameserver.model.L2World;
import com.l2jmobius.gameserver.model.actor.instance.L2PcInstance;
import com.l2jmobius.gameserver.model.entity.siege.Castle;
import com.l2jmobius.gameserver.network.SystemMessageId;

/**
 * Class For Castle Manor Manager Load manor data from DB Update/Reload/Delete Handles all schedule for manor
 * @author l3x
 */
public class CastleManorManager
{
	protected static Logger LOGGER = Logger.getLogger(CastleManorManager.class.getName());
	
	private static CastleManorManager _instance;
	
	public static final int PERIOD_CURRENT = 0;
	public static final int PERIOD_NEXT = 1;
	public static int APPROVE = -1;
	
	private static final String CASTLE_MANOR_LOAD_PROCURE = "SELECT * FROM castle_manor_procure WHERE castle_id=?";
	private static final String CASTLE_MANOR_LOAD_PRODUCTION = "SELECT * FROM castle_manor_production WHERE castle_id=?";
	
	protected static final int NEXT_PERIOD_APPROVE = Config.ALT_MANOR_APPROVE_TIME; // 6:00
	protected static final int NEXT_PERIOD_APPROVE_MIN = Config.ALT_MANOR_APPROVE_MIN; //
	protected static final int MANOR_REFRESH = Config.ALT_MANOR_REFRESH_TIME; // 20:00
	protected static final int MANOR_REFRESH_MIN = Config.ALT_MANOR_REFRESH_MIN; //
	protected static final long MAINTENANCE_PERIOD = Config.ALT_MANOR_MAINTENANCE_PERIOD / 60000; // 6 mins
	
	private boolean _underMaintenance;
	private boolean _disabled;
	
	public static CastleManorManager getInstance()
	{
		if (_instance == null)
		{
			LOGGER.info("Initializing CastleManorManager");
			_instance = new CastleManorManager();
		}
		return _instance;
	}
	
	public class CropProcure
	{
		int _cropId;
		int _buyResidual;
		int _rewardType;
		int _buy;
		int _price;
		
		public CropProcure(int id)
		{
			_cropId = id;
			_buyResidual = 0;
			_rewardType = 0;
			_buy = 0;
			_price = 0;
		}
		
		public CropProcure(int id, int amount, int type, int buy, int price)
		{
			_cropId = id;
			_buyResidual = amount;
			_rewardType = type;
			_buy = buy;
			_price = price;
		}
		
		public int getReward()
		{
			return _rewardType;
		}
		
		public int getId()
		{
			return _cropId;
		}
		
		public int getAmount()
		{
			return _buyResidual;
		}
		
		public int getStartAmount()
		{
			return _buy;
		}
		
		public int getPrice()
		{
			return _price;
		}
		
		public void setAmount(int amount)
		{
			_buyResidual = amount;
		}
	}
	
	public class SeedProduction
	{
		int _seedId;
		int _residual;
		int _price;
		int _sales;
		
		public SeedProduction(int id)
		{
			_seedId = id;
			_sales = 0;
			_price = 0;
			_sales = 0;
		}
		
		public SeedProduction(int id, int amount, int price, int sales)
		{
			_seedId = id;
			_residual = amount;
			_price = price;
			_sales = sales;
		}
		
		public int getId()
		{
			return _seedId;
		}
		
		public int getCanProduce()
		{
			return _residual;
		}
		
		public int getPrice()
		{
			return _price;
		}
		
		public int getStartProduce()
		{
			return _sales;
		}
		
		public void setCanProduce(int amount)
		{
			_residual = amount;
		}
	}
	
	private CastleManorManager()
	{
		load(); // load data from database
		init(); // schedule all manor related events
		_underMaintenance = false;
		_disabled = !Config.ALLOW_MANOR;
		for (Castle c : CastleManager.getInstance().getCastles())
		{
			c.setNextPeriodApproved(APPROVE == 1 ? true : false);
		}
	}
	
	private void load()
	{
		ResultSet rs = null;
		PreparedStatement statement = null;
		try (Connection con = DatabaseFactory.getInstance().getConnection())
		{
			for (Castle castle : CastleManager.getInstance().getCastles())
			{
				final List<SeedProduction> production = new ArrayList<>();
				final List<SeedProduction> productionNext = new ArrayList<>();
				final List<CropProcure> procure = new ArrayList<>();
				final List<CropProcure> procureNext = new ArrayList<>();
				
				// restore seed production info
				statement = con.prepareStatement(CASTLE_MANOR_LOAD_PRODUCTION);
				statement.setInt(1, castle.getCastleId());
				rs = statement.executeQuery();
				while (rs.next())
				{
					final int seedId = rs.getInt("seed_id");
					final int canProduce = rs.getInt("can_produce");
					final int startProduce = rs.getInt("start_produce");
					final int price = rs.getInt("seed_price");
					final int period = rs.getInt("period");
					if (period == PERIOD_CURRENT)
					{
						production.add(new SeedProduction(seedId, canProduce, price, startProduce));
					}
					else
					{
						productionNext.add(new SeedProduction(seedId, canProduce, price, startProduce));
					}
				}
				statement.close();
				rs.close();
				
				castle.setSeedProduction(production, PERIOD_CURRENT);
				castle.setSeedProduction(productionNext, PERIOD_NEXT);
				
				// restore procure info
				statement = con.prepareStatement(CASTLE_MANOR_LOAD_PROCURE);
				statement.setInt(1, castle.getCastleId());
				rs = statement.executeQuery();
				while (rs.next())
				{
					final int cropId = rs.getInt("crop_id");
					final int canBuy = rs.getInt("can_buy");
					final int startBuy = rs.getInt("start_buy");
					final int rewardType = rs.getInt("reward_type");
					final int price = rs.getInt("price");
					final int period = rs.getInt("period");
					if (period == PERIOD_CURRENT)
					{
						procure.add(new CropProcure(cropId, canBuy, rewardType, startBuy, price));
					}
					else
					{
						procureNext.add(new CropProcure(cropId, canBuy, rewardType, startBuy, price));
					}
				}
				statement.close();
				rs.close();
				
				castle.setCropProcure(procure, PERIOD_CURRENT);
				castle.setCropProcure(procureNext, PERIOD_NEXT);
				
				if (!procure.isEmpty() || !procureNext.isEmpty() || !production.isEmpty() || !productionNext.isEmpty())
				{
					LOGGER.info(castle.getName() + ": Data loaded");
				}
			}
		}
		catch (Exception e)
		{
			LOGGER.info("Error restoring manor data: " + e.getMessage());
		}
	}
	
	protected void init()
	{
		if (APPROVE == -1)
		{
			final Calendar manorRefresh = Calendar.getInstance();
			manorRefresh.set(Calendar.HOUR_OF_DAY, MANOR_REFRESH);
			manorRefresh.set(Calendar.MINUTE, MANOR_REFRESH_MIN);
			manorRefresh.set(Calendar.SECOND, 0);
			manorRefresh.set(Calendar.MILLISECOND, 0);
			
			final Calendar periodApprove = Calendar.getInstance();
			periodApprove.set(Calendar.HOUR_OF_DAY, NEXT_PERIOD_APPROVE);
			periodApprove.set(Calendar.MINUTE, NEXT_PERIOD_APPROVE_MIN);
			periodApprove.set(Calendar.SECOND, 0);
			periodApprove.set(Calendar.MILLISECOND, 0);
			final boolean isApproved = (periodApprove.getTimeInMillis() < Calendar.getInstance().getTimeInMillis()) && (manorRefresh.getTimeInMillis() > Calendar.getInstance().getTimeInMillis());
			APPROVE = isApproved ? 1 : 0;
		}
		
		final Calendar FirstDelay = Calendar.getInstance();
		FirstDelay.set(Calendar.SECOND, 0);
		FirstDelay.set(Calendar.MILLISECOND, 0);
		FirstDelay.add(Calendar.MINUTE, 1);
		ThreadPoolManager.scheduleAtFixedRate(new ManorTask(), FirstDelay.getTimeInMillis() - Calendar.getInstance().getTimeInMillis(), 60000);
	}
	
	public void setNextPeriod()
	{
		for (Castle c : CastleManager.getInstance().getCastles())
		{
			c.setNextPeriodApproved(false);
			
			if (c.getOwnerId() <= 0)
			{
				continue;
			}
			
			final L2Clan clan = ClanTable.getInstance().getClan(c.getOwnerId());
			if (clan == null)
			{
				continue;
			}
			
			final ItemContainer cwh = clan.getWarehouse();
			if (!(cwh instanceof ClanWarehouse))
			{
				LOGGER.info("Can't get clan warehouse for clan " + ClanTable.getInstance().getClan(c.getOwnerId()));
				return;
			}
			
			for (CropProcure crop : c.getCropProcure(PERIOD_CURRENT))
			{
				if (crop.getStartAmount() == 0)
				{
					continue;
				}
				
				// adding bought crops to clan warehouse
				if (crop.getStartAmount() > crop.getAmount())
				{
					int count = crop.getStartAmount() - crop.getAmount();
					
					count = (count * 90) / 100;
					if ((count < 1) && (Rnd.get(99) < 90))
					{
						count = 1;
					}
					
					if (count >= 1)
					{
						cwh.addItem("Manor", L2Manor.getInstance().getMatureCrop(crop.getId()), count, null, null);
					}
				}
				
				// reserved and not used money giving back to treasury
				if (crop.getAmount() > 0)
				{
					c.addToTreasuryNoTax(crop.getAmount() * crop.getPrice());
				}
			}
			
			c.setSeedProduction(c.getSeedProduction(PERIOD_NEXT), PERIOD_CURRENT);
			c.setCropProcure(c.getCropProcure(PERIOD_NEXT), PERIOD_CURRENT);
			
			int manor_cost = c.getManorCost(PERIOD_CURRENT);
			if (c.getTreasury() < manor_cost)
			{
				c.setSeedProduction(getNewSeedsList(c.getCastleId()), PERIOD_NEXT);
				c.setCropProcure(getNewCropsList(c.getCastleId()), PERIOD_NEXT);
				manor_cost = c.getManorCost(PERIOD_CURRENT);
				if (manor_cost > 0)
				{
					LOGGER.info(c.getName() + "|" + -manor_cost + "|ManorManager Error@setNextPeriod");
				}
			}
			else
			{
				final List<SeedProduction> production = new ArrayList<>();
				final List<CropProcure> procure = new ArrayList<>();
				for (SeedProduction s : c.getSeedProduction(PERIOD_CURRENT))
				{
					s.setCanProduce(s.getStartProduce());
					production.add(s);
				}
				for (CropProcure cr : c.getCropProcure(PERIOD_CURRENT))
				{
					cr.setAmount(cr.getStartAmount());
					procure.add(cr);
				}
				c.setSeedProduction(production, PERIOD_NEXT);
				c.setCropProcure(procure, PERIOD_NEXT);
			}
			
			if (Config.ALT_MANOR_SAVE_ALL_ACTIONS)
			{
				c.saveCropData();
				c.saveSeedData();
			}
			
			// Sending notification to a clan leader
			L2PcInstance clanLeader = null;
			if ((clan.getLeader() != null) && (clan.getLeader().getName() != null))
			{
				clanLeader = L2World.getInstance().getPlayer(clan.getLeader().getName());
			}
			
			if (clanLeader != null)
			{
				clanLeader.sendPacket(SystemMessageId.THE_MANOR_INFORMATION_HAS_BEEN_UPDATED);
			}
			
			c.setNextPeriodApproved(false);
		}
	}
	
	public void approveNextPeriod()
	{
		for (Castle c : CastleManager.getInstance().getCastles())
		{
			// Castle has no owner
			if (c.getOwnerId() > 0)
			{
				int manor_cost = c.getManorCost(PERIOD_NEXT);
				
				if (c.getTreasury() < manor_cost)
				{
					c.setSeedProduction(getNewSeedsList(c.getCastleId()), PERIOD_NEXT);
					c.setCropProcure(getNewCropsList(c.getCastleId()), PERIOD_NEXT);
					manor_cost = c.getManorCost(PERIOD_NEXT);
					if (manor_cost > 0)
					{
						LOGGER.info(c.getName() + "|" + -manor_cost + "|ManorManager Error@approveNextPeriod");
					}
					final L2Clan clan = ClanTable.getInstance().getClan(c.getOwnerId());
					L2PcInstance clanLeader = null;
					if (clan != null)
					{
						clanLeader = L2World.getInstance().getPlayer(clan.getLeader().getName());
					}
					if (clanLeader != null)
					{
						clanLeader.sendPacket(SystemMessageId.THE_AMOUNT_IS_NOT_SUFFICIENT_AND_SO_THE_MANOR_IS_NOT_IN_OPERATION);
					}
				}
				else
				{
					c.addToTreasuryNoTax(-manor_cost);
					LOGGER.info(c.getName() + "|" + -manor_cost + "|ManorManager");
				}
			}
			c.setNextPeriodApproved(true);
		}
	}
	
	private List<SeedProduction> getNewSeedsList(int castleId)
	{
		final List<SeedProduction> seeds = new ArrayList<>();
		final List<Integer> seedsIds = L2Manor.getInstance().getSeedsForCastle(castleId);
		for (int sd : seedsIds)
		{
			seeds.add(new SeedProduction(sd));
		}
		return seeds;
	}
	
	private List<CropProcure> getNewCropsList(int castleId)
	{
		final List<CropProcure> crops = new ArrayList<>();
		final List<Integer> cropsIds = L2Manor.getInstance().getCropsForCastle(castleId);
		for (int cr : cropsIds)
		{
			crops.add(new CropProcure(cr));
		}
		return crops;
	}
	
	public boolean isUnderMaintenance()
	{
		return _underMaintenance;
	}
	
	public void setUnderMaintenance(boolean mode)
	{
		_underMaintenance = mode;
	}
	
	public boolean isDisabled()
	{
		return _disabled;
	}
	
	public void setDisabled(boolean mode)
	{
		_disabled = mode;
	}
	
	public SeedProduction getNewSeedProduction(int id, int amount, int price, int sales)
	{
		return new SeedProduction(id, amount, price, sales);
	}
	
	public CropProcure getNewCropProcure(int id, int amount, int type, int price, int buy)
	{
		return new CropProcure(id, amount, type, buy, price);
	}
	
	public void save()
	{
		for (Castle c : CastleManager.getInstance().getCastles())
		{
			c.saveSeedData();
			c.saveCropData();
		}
	}
	
	protected class ManorTask implements Runnable
	{
		@Override
		public void run()
		{
			final int H = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
			final int M = Calendar.getInstance().get(Calendar.MINUTE);
			
			if (APPROVE == 1) // 06:00 - 20:00
			{
				if ((H < NEXT_PERIOD_APPROVE) || (H > MANOR_REFRESH) || ((H == MANOR_REFRESH) && (M >= MANOR_REFRESH_MIN)))
				{
					APPROVE = 0;
					setUnderMaintenance(true);
					LOGGER.info("Manor System: Under maintenance mode started");
				}
			}
			else if (isUnderMaintenance()) // 20:00 - 20:06
			{
				if ((H != MANOR_REFRESH) || (M >= (MANOR_REFRESH_MIN + MAINTENANCE_PERIOD)))
				{
					setUnderMaintenance(false);
					LOGGER.info("Manor System: Next period started");
					if (isDisabled())
					{
						return;
					}
					setNextPeriod();
					try
					{
						save();
					}
					catch (Exception e)
					{
						LOGGER.info("Manor System: Failed to save manor data: " + e);
					}
				}
			}
			else if (((H > NEXT_PERIOD_APPROVE) && (H < MANOR_REFRESH)) || ((H == NEXT_PERIOD_APPROVE) && (M >= NEXT_PERIOD_APPROVE_MIN)))
			{
				APPROVE = 1;
				LOGGER.info("Manor System: Next period approved");
				if (isDisabled())
				{
					return;
				}
				approveNextPeriod();
			}
		}
	}
}