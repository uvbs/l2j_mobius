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
package com.l2jmobius.gameserver.model;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.logging.Logger;

import com.l2jmobius.Config;
import com.l2jmobius.commons.database.DatabaseFactory;
import com.l2jmobius.gameserver.datatables.SkillTable;
import com.l2jmobius.gameserver.datatables.xml.AugmentationData;
import com.l2jmobius.gameserver.model.actor.instance.L2ItemInstance;
import com.l2jmobius.gameserver.model.actor.instance.L2PcInstance;
import com.l2jmobius.gameserver.skills.Stats;
import com.l2jmobius.gameserver.skills.funcs.FuncAdd;
import com.l2jmobius.gameserver.skills.funcs.LambdaConst;

/**
 * Used to store an augmentation and its boni
 * @author durgus
 */
public final class L2Augmentation
{
	private static final Logger LOGGER = Logger.getLogger(L2Augmentation.class.getName());
	
	private final L2ItemInstance _item;
	private int _effectsId = 0;
	private augmentationStatBoni _boni = null;
	private L2Skill _skill = null;
	
	public L2Augmentation(L2ItemInstance item, int effects, L2Skill skill, boolean save)
	{
		_item = item;
		_effectsId = effects;
		_boni = new augmentationStatBoni(_effectsId);
		_skill = skill;
		
		// write to DB if save is true
		if (save)
		{
			saveAugmentationData();
		}
	}
	
	public L2Augmentation(L2ItemInstance item, int effects, int skill, int skillLevel, boolean save)
	{
		this(item, effects, SkillTable.getInstance().getInfo(skill, skillLevel), save);
	}
	
	// =========================================================
	// Nested Class
	
	public class augmentationStatBoni
	{
		private final Stats _stats[];
		private final float _values[];
		private boolean _active;
		
		public augmentationStatBoni(int augmentationId)
		{
			_active = false;
			List<AugmentationData.AugStat> as = AugmentationData.getInstance().getAugStatsById(augmentationId);
			
			_stats = new Stats[as.size()];
			_values = new float[as.size()];
			
			int i = 0;
			for (AugmentationData.AugStat aStat : as)
			{
				_stats[i] = aStat.getStat();
				_values[i] = aStat.getValue();
				i++;
			}
		}
		
		public void applyBoni(L2PcInstance player)
		{
			// make sure the boni are not applyed twice..
			if (_active)
			{
				return;
			}
			
			for (int i = 0; i < _stats.length; i++)
			{
				player.addStatFunc(new FuncAdd(_stats[i], 0x40, this, new LambdaConst(_values[i])));
			}
			
			_active = true;
		}
		
		public void removeBoni(L2PcInstance player)
		{
			// make sure the boni is not removed twice
			if (!_active)
			{
				return;
			}
			
			player.removeStatsOwner(this);
			
			_active = false;
		}
	}
	
	private void saveAugmentationData()
	{
		try (Connection con = DatabaseFactory.getInstance().getConnection())
		{
			PreparedStatement statement = con.prepareStatement("INSERT INTO augmentations (item_id,attributes,skill,level) VALUES (?,?,?,?)");
			statement.setInt(1, _item.getObjectId());
			statement.setInt(2, _effectsId);
			
			if (_skill != null)
			{
				statement.setInt(3, _skill.getId());
				statement.setInt(4, _skill.getLevel());
			}
			else
			{
				statement.setInt(3, 0);
				statement.setInt(4, 0);
			}
			
			statement.executeUpdate();
			statement.close();
		}
		catch (Exception e)
		{
			LOGGER.warning("Could not save augmentation for item: " + _item.getObjectId() + " from DB: " + e);
		}
	}
	
	public void deleteAugmentationData()
	{
		if (!_item.isAugmented())
		{
			return;
		}
		
		// delete the augmentation from the database
		try (Connection con = DatabaseFactory.getInstance().getConnection())
		{
			PreparedStatement statement = con.prepareStatement("DELETE FROM augmentations WHERE item_id=?");
			statement.setInt(1, _item.getObjectId());
			statement.executeUpdate();
			statement.close();
		}
		catch (Exception e)
		{
			LOGGER.warning("Could not delete augmentation for item: " + _item.getObjectId() + " from DB: " + e);
		}
	}
	
	/**
	 * Get the augmentation "id" used in serverpackets.
	 * @return augmentationId
	 */
	public int getAugmentationId()
	{
		return _effectsId;
	}
	
	public L2Skill getSkill()
	{
		return _skill;
	}
	
	/**
	 * Applys the boni to the player.
	 * @param player
	 */
	public void applyBoni(L2PcInstance player)
	{
		_boni.applyBoni(player);
		
		// add the skill if any
		if (_skill != null)
		{
			
			player.addSkill(_skill);
			
			if (_skill.isActive() && (Config.ACTIVE_AUGMENTS_START_REUSE_TIME > 0))
			{
				player.disableSkill(_skill, Config.ACTIVE_AUGMENTS_START_REUSE_TIME);
				player.addTimeStamp(_skill, Config.ACTIVE_AUGMENTS_START_REUSE_TIME);
			}
			
			player.sendSkillList();
		}
	}
	
	/**
	 * Removes the augmentation boni from the player.
	 * @param player
	 */
	public void removeBoni(L2PcInstance player)
	{
		_boni.removeBoni(player);
		
		// remove the skill if any
		if (_skill != null)
		{
			if (_skill.isPassive())
			{
				player.removeSkill(_skill);
			}
			else
			{
				player.removeSkill(_skill, false);
			}
			
			if ((_skill.isPassive() && Config.DELETE_AUGM_PASSIVE_ON_CHANGE) || (_skill.isActive() && Config.DELETE_AUGM_ACTIVE_ON_CHANGE))
			{
				
				// Iterate through all effects currently on the character.
				final L2Effect[] effects = player.getAllEffects();
				
				for (L2Effect currenteffect : effects)
				{
					final L2Skill effectSkill = currenteffect.getSkill();
					
					if (effectSkill.getId() == _skill.getId())
					{
						player.sendMessage("You feel the power of " + effectSkill.getName() + " leaving yourself.");
						currenteffect.exit(false);
					}
				}
				
			}
			
			player.sendSkillList();
		}
	}
}