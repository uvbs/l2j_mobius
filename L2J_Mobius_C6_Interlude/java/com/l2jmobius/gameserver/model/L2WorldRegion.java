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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Logger;

import com.l2jmobius.Config;
import com.l2jmobius.commons.concurrent.ThreadPoolManager;
import com.l2jmobius.commons.util.object.L2ObjectSet;
import com.l2jmobius.gameserver.ai.L2AttackableAI;
import com.l2jmobius.gameserver.ai.L2FortSiegeGuardAI;
import com.l2jmobius.gameserver.ai.L2SiegeGuardAI;
import com.l2jmobius.gameserver.datatables.sql.SpawnTable;
import com.l2jmobius.gameserver.model.actor.L2Attackable;
import com.l2jmobius.gameserver.model.actor.L2Character;
import com.l2jmobius.gameserver.model.actor.L2Playable;
import com.l2jmobius.gameserver.model.actor.instance.L2NpcInstance;
import com.l2jmobius.gameserver.model.spawn.L2Spawn;
import com.l2jmobius.gameserver.model.zone.L2ZoneManager;
import com.l2jmobius.gameserver.model.zone.L2ZoneType;
import com.l2jmobius.gameserver.model.zone.type.L2PeaceZone;

/**
 * This class ...
 * @version $Revision: 1.3.4.4 $ $Date: 2005/03/27 15:29:33 $
 */
public final class L2WorldRegion
{
	private static Logger LOGGER = Logger.getLogger(L2WorldRegion.class.getName());
	
	/**
	 * L2ObjectHashSet(L2PlayableInstance) containing L2PlayableInstance of all player & summon in game in this L2WorldRegion
	 */
	private final L2ObjectSet<L2Playable> _allPlayable;
	
	/** L2ObjectHashSet(L2Object) containing L2Object visible in this L2WorldRegion */
	private final L2ObjectSet<L2Object> _visibleObjects;
	
	private final List<L2WorldRegion> _surroundingRegions;
	private final int _tileX, _tileY;
	private Boolean _active = false;
	private ScheduledFuture<?> _neighborsTask = null;
	
	private L2ZoneManager _zoneManager;
	
	public L2WorldRegion(int pTileX, int pTileY)
	{
		_allPlayable = L2ObjectSet.createL2PlayerSet(); // new L2ObjectHashSet<L2PcInstance>();
		_visibleObjects = L2ObjectSet.createL2ObjectSet(); // new L2ObjectHashSet<L2Object>();
		_surroundingRegions = new ArrayList<>();
		// _surroundingRegions.add(this); //done in L2World.initRegions()
		
		_tileX = pTileX;
		_tileY = pTileY;
		
		// default a newly initialized region to inactive, unless always on is specified
		if (Config.GRIDS_ALWAYS_ON)
		{
			_active = true;
		}
		else
		{
			_active = false;
		}
	}
	
	public void addZone(L2ZoneType zone)
	{
		if (_zoneManager == null)
		{
			_zoneManager = new L2ZoneManager();
		}
		_zoneManager.registerNewZone(zone);
	}
	
	public void removeZone(L2ZoneType zone)
	{
		if (_zoneManager == null)
		{
			return;
		}
		
		_zoneManager.unregisterZone(zone);
	}
	
	public void revalidateZones(L2Character character)
	{
		if (_zoneManager == null)
		{
			return;
		}
		
		if (_zoneManager != null)
		{
			_zoneManager.revalidateZones(character);
		}
	}
	
	public void removeFromZones(L2Character character)
	{
		if (_zoneManager == null)
		{
			return;
		}
		
		if (_zoneManager != null)
		{
			_zoneManager.removeCharacter(character);
		}
	}
	
	public void onDeath(L2Character character)
	{
		if (_zoneManager == null)
		{
			return;
		}
		
		if (_zoneManager != null)
		{
			_zoneManager.onDeath(character);
		}
	}
	
	public void onRevive(L2Character character)
	{
		if (_zoneManager == null)
		{
			return;
		}
		
		if (_zoneManager != null)
		{
			_zoneManager.onRevive(character);
		}
	}
	
	/** Task of AI notification */
	public class NeighborsTask implements Runnable
	{
		private final boolean _isActivating;
		
		public NeighborsTask(boolean isActivating)
		{
			_isActivating = isActivating;
		}
		
		@Override
		public void run()
		{
			if (_isActivating)
			{
				// for each neighbor, if it's not active, activate.
				for (L2WorldRegion neighbor : getSurroundingRegions())
				{
					neighbor.setActive(true);
				}
			}
			else
			{
				if (areNeighborsEmpty())
				{
					setActive(false);
				}
				
				// check and deactivate
				for (L2WorldRegion neighbor : getSurroundingRegions())
				{
					if (neighbor.areNeighborsEmpty())
					{
						neighbor.setActive(false);
					}
				}
			}
		}
	}
	
	private void switchAI(Boolean isOn)
	{
		int c = 0;
		
		if (!isOn)
		{
			for (L2Object o : _visibleObjects)
			{
				if (o instanceof L2Attackable)
				{
					c++;
					final L2Attackable mob = (L2Attackable) o;
					
					// Set target to null and cancel Attack or Cast
					mob.setTarget(null);
					
					// Stop movement
					mob.stopMove(null);
					
					// Stop all active skills effects in progress on the L2Character
					mob.stopAllEffects();
					
					mob.clearAggroList();
					mob.getKnownList().removeAllKnownObjects();
					
					if (mob.getAI() != null)
					{
						mob.getAI().setIntention(com.l2jmobius.gameserver.ai.CtrlIntention.AI_INTENTION_IDLE);
						
						// stop the ai tasks
						if (mob.getAI() instanceof L2AttackableAI)
						{
							((L2AttackableAI) mob.getAI()).stopAITask();
						}
						else if (mob.getAI() instanceof L2FortSiegeGuardAI)
						{
							((L2FortSiegeGuardAI) mob.getAI()).stopAITask();
						}
						else if (mob.getAI() instanceof L2SiegeGuardAI)
						{
							((L2SiegeGuardAI) mob.getAI()).stopAITask();
						}
					}
					
					// Stop HP/MP/CP Regeneration task
					// try this: allow regen, but only until mob is 100% full...then stop
					// it until the grid is made active.
					// mob.getStatus().stopHpMpRegeneration();
				}
			}
			if (Config.DEBUG)
			{
				LOGGER.info(c + " mobs were turned off");
			}
		}
		else
		{
			for (L2Object o : _visibleObjects)
			{
				if (o instanceof L2Attackable)
				{
					c++;
					// Start HP/MP/CP Regeneration task
					((L2Attackable) o).getStatus().startHpMpRegeneration();
					
					// start the ai
					// ((L2AttackableAI) mob.getAI()).startAITask();
				}
				else if (o instanceof L2NpcInstance)
				{
					// Create a RandomAnimation Task that will be launched after the calculated delay if the server allow it
					// L2Monsterinstance/L2Attackable socials are handled by AI (TODO: check the instances)
					((L2NpcInstance) o).startRandomAnimationTimer();
				}
			}
			if (Config.DEBUG)
			{
				LOGGER.info(c + " mobs were turned on");
			}
		}
	}
	
	public Boolean isActive()
	{
		return _active;
	}
	
	// check if all 9 neighbors (including self) are inactive or active but with no players.
	// returns true if the above condition is met.
	public Boolean areNeighborsEmpty()
	{
		// if this region is occupied, return false.
		if (isActive() && (_allPlayable.size() > 0))
		{
			return false;
		}
		
		// if any one of the neighbors is occupied, return false
		for (L2WorldRegion neighbor : _surroundingRegions)
		{
			if (neighbor.isActive() && (neighbor._allPlayable.size() > 0))
			{
				return false;
			}
		}
		
		// in all other cases, return true.
		return true;
	}
	
	/**
	 * this function turns this region's AI and geodata on or off
	 * @param value
	 */
	public void setActive(boolean value)
	{
		if (_active == value)
		{
			return;
		}
		
		_active = value;
		
		// turn the AI on or off to match the region's activation.
		switchAI(value);
		
		// TODO
		// turn the geodata on or off to match the region's activation.
		if (Config.DEBUG)
		{
			if (value)
			{
				LOGGER.info("Starting Grid " + _tileX + "," + _tileY);
			}
			else
			{
				LOGGER.info("Stoping Grid " + _tileX + "," + _tileY);
			}
		}
	}
	
	/**
	 * Immediately sets self as active and starts a timer to set neighbors as active this timer is to avoid turning on neighbors in the case when a person just teleported into a region and then teleported out immediately...there is no reason to activate all the neighbors in that case.
	 */
	private void startActivation()
	{
		// first set self to active and do self-tasks...
		setActive(true);
		
		// if the timer to deactivate neighbors is running, cancel it.
		if (_neighborsTask != null)
		{
			_neighborsTask.cancel(true);
			_neighborsTask = null;
		}
		
		// then, set a timer to activate the neighbors
		_neighborsTask = ThreadPoolManager.schedule(new NeighborsTask(true), 1000 * Config.GRID_NEIGHBOR_TURNON_TIME);
	}
	
	/**
	 * starts a timer to set neighbors (including self) as inactive this timer is to avoid turning off neighbors in the case when a person just moved out of a region that he may very soon return to. There is no reason to turn self & neighbors off in that case.
	 */
	private void startDeactivation()
	{
		// if the timer to activate neighbors is running, cancel it.
		if (_neighborsTask != null)
		{
			_neighborsTask.cancel(true);
			_neighborsTask = null;
		}
		
		// start a timer to "suggest" a deactivate to self and neighbors.
		// suggest means: first check if a neighbor has L2PcInstances in it. If not, deactivate.
		_neighborsTask = ThreadPoolManager.schedule(new NeighborsTask(false), 1000 * Config.GRID_NEIGHBOR_TURNOFF_TIME);
	}
	
	/**
	 * Add the L2Object in the L2ObjectHashSet(L2Object) _visibleObjects containing L2Object visible in this L2WorldRegion <BR>
	 * If L2Object is a L2PcInstance, Add the L2PcInstance in the L2ObjectHashSet(L2PcInstance) _allPlayable containing L2PcInstance of all player in game in this L2WorldRegion <BR>
	 * Assert : object.getCurrentWorldRegion() == this
	 * @param object
	 */
	public void addVisibleObject(L2Object object)
	{
		if (Config.ASSERT)
		{
			assert object.getWorldRegion() == this;
		}
		
		if (object == null)
		{
			return;
		}
		
		_visibleObjects.put(object);
		
		if (object instanceof L2Playable)
		{
			_allPlayable.put((L2Playable) object);
			
			// if this is the first player to enter the region, activate self & neighbors
			if ((_allPlayable.size() == 1) && !Config.GRIDS_ALWAYS_ON)
			{
				startActivation();
			}
		}
	}
	
	/**
	 * Remove the L2Object from the L2ObjectHashSet(L2Object) _visibleObjects in this L2WorldRegion <BR>
	 * <BR>
	 * If L2Object is a L2PcInstance, remove it from the L2ObjectHashSet(L2PcInstance) _allPlayable of this L2WorldRegion <BR>
	 * Assert : object.getCurrentWorldRegion() == this || object.getCurrentWorldRegion() == null
	 * @param object
	 */
	public void removeVisibleObject(L2Object object)
	{
		if (Config.ASSERT)
		{
			assert (object.getWorldRegion() == this) || (object.getWorldRegion() == null);
		}
		
		if (object == null)
		{
			return;
		}
		
		_visibleObjects.remove(object);
		
		if (object instanceof L2Playable)
		{
			_allPlayable.remove((L2Playable) object);
			
			if ((_allPlayable.size() == 0) && !Config.GRIDS_ALWAYS_ON)
			{
				startDeactivation();
			}
		}
	}
	
	public void addSurroundingRegion(L2WorldRegion region)
	{
		_surroundingRegions.add(region);
	}
	
	/**
	 * @return the list _surroundingRegions containing all L2WorldRegion around the current L2WorldRegion
	 */
	public List<L2WorldRegion> getSurroundingRegions()
	{
		// change to return L2WorldRegion[] ?
		// this should not change after initialization, so maybe changes are not necessary
		
		return _surroundingRegions;
	}
	
	public Iterator<L2Playable> iterateAllPlayers()
	{
		return _allPlayable.iterator();
	}
	
	public L2ObjectSet<L2Object> getVisibleObjects()
	{
		return _visibleObjects;
	}
	
	public String getName()
	{
		return "(" + _tileX + ", " + _tileY + ")";
	}
	
	/**
	 * Deleted all spawns in the world.
	 */
	public synchronized void deleteVisibleNpcSpawns()
	{
		LOGGER.info("Deleting all visible NPC's in Region: " + getName());
		for (L2Object obj : _visibleObjects)
		{
			if (obj instanceof L2NpcInstance)
			{
				L2NpcInstance target = (L2NpcInstance) obj;
				target.deleteMe();
				L2Spawn spawn = target.getSpawn();
				
				if (spawn != null)
				{
					spawn.stopRespawn();
					SpawnTable.getInstance().deleteSpawn(spawn, false);
				}
				
				LOGGER.info("Removed NPC " + target.getObjectId());
			}
		}
		if (Config.DEBUG)
		{
			LOGGER.info("All visible NPC's deleted in Region: " + getName());
		}
	}
	
	/**
	 * @param skill
	 * @param x
	 * @param y
	 * @param z
	 * @return
	 */
	public boolean checkEffectRangeInsidePeaceZone(L2Skill skill, int x, int y, int z)
	{
		if (_zoneManager != null)
		{
			final int range = skill.getEffectRange();
			final int up = y + range;
			final int down = y - range;
			final int left = x + range;
			final int right = x - range;
			
			for (L2ZoneType e : _zoneManager.getZones())
			{
				if (e instanceof L2PeaceZone)
				{
					if (e.isInsideZone(x, up, z))
					{
						return false;
					}
					
					if (e.isInsideZone(x, down, z))
					{
						return false;
					}
					
					if (e.isInsideZone(left, y, z))
					{
						return false;
					}
					
					if (e.isInsideZone(right, y, z))
					{
						return false;
					}
					
					if (e.isInsideZone(x, y, z))
					{
						return false;
					}
				}
			}
			return true;
		}
		return true;
	}
}