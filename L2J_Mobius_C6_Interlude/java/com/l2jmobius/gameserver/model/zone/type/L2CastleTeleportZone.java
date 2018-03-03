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
package com.l2jmobius.gameserver.model.zone.type;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.l2jmobius.commons.util.Rnd;
import com.l2jmobius.gameserver.instancemanager.CastleManager;
import com.l2jmobius.gameserver.model.actor.L2Character;
import com.l2jmobius.gameserver.model.actor.instance.L2PcInstance;
import com.l2jmobius.gameserver.model.entity.siege.Castle;
import com.l2jmobius.gameserver.model.zone.L2ZoneType;
import com.l2jmobius.gameserver.model.zone.ZoneId;

public class L2CastleTeleportZone extends L2ZoneType
{
	
	private final int _spawnLoc[];
	private int _castleId;
	private Castle _castle;
	
	public L2CastleTeleportZone(int id)
	{
		super(id);
		_spawnLoc = new int[5];
	}
	
	@Override
	public void setParameter(String name, String value)
	{
		switch (name)
		{
			case "castleId":
			{
				_castleId = Integer.parseInt(value);
				_castle = CastleManager.getInstance().getCastleById(_castleId);
				_castle.setTeleZone(this);
				break;
			}
			case "spawnMinX":
			{
				_spawnLoc[0] = Integer.parseInt(value);
				break;
			}
			case "spawnMaxX":
			{
				_spawnLoc[1] = Integer.parseInt(value);
				break;
			}
			case "spawnMinY":
			{
				_spawnLoc[2] = Integer.parseInt(value);
				break;
			}
			case "spawnMaxY":
			{
				_spawnLoc[3] = Integer.parseInt(value);
				break;
			}
			case "spawnZ":
			{
				_spawnLoc[4] = Integer.parseInt(value);
				break;
			}
			default:
			{
				super.setParameter(name, value);
				break;
			}
		}
	}
	
	@Override
	protected void onEnter(L2Character character)
	{
		character.setInsideZone(ZoneId.OLY, true);
	}
	
	@Override
	protected void onExit(L2Character character)
	{
		character.setInsideZone(ZoneId.OLY, false);
	}
	
	@Override
	public void onDieInside(L2Character l2character)
	{
	}
	
	@Override
	public void onReviveInside(L2Character l2character)
	{
	}
	
	public List<L2Character> getAllPlayers()
	{
		final List<L2Character> players = new ArrayList<>();
		Iterator<L2Character> i$ = _characterList.values().iterator();
		
		while (i$.hasNext())
		{
			L2Character temp = i$.next();
			
			if (temp instanceof L2PcInstance)
			{
				players.add(temp);
			}
		}
		
		return players;
	}
	
	public void oustAllPlayers()
	{
		if (_characterList == null)
		{
			return;
		}
		
		if (_characterList.isEmpty())
		{
			return;
		}
		
		Iterator<L2Character> i$ = _characterList.values().iterator();
		while (i$.hasNext())
		{
			L2Character character = i$.next();
			
			if ((character != null) && (character instanceof L2PcInstance))
			{
				L2PcInstance player = (L2PcInstance) character;
				
				if (player.isOnline() == 1)
				{
					player.teleToLocation(Rnd.get(_spawnLoc[0], _spawnLoc[1]), Rnd.get(_spawnLoc[2], _spawnLoc[3]), _spawnLoc[4]);
				}
			}
		}
	}
	
	public int[] getSpawn()
	{
		return _spawnLoc;
	}
}