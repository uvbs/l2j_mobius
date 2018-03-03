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

import com.l2jmobius.gameserver.model.actor.L2Character;
import com.l2jmobius.gameserver.model.actor.instance.L2PcInstance;
import com.l2jmobius.gameserver.model.zone.L2ZoneType;

public class L2CustomZone extends L2ZoneType
{
	
	public L2CustomZone(int id)
	{
		super(id);
		_IsFlyingEnable = true;
	}
	
	@Override
	protected void onDieInside(L2Character l2character)
	{
	}
	
	@Override
	protected void onReviveInside(L2Character l2character)
	{
	}
	
	@Override
	public void setParameter(String name, String value)
	{
		switch (name)
		{
			case "name":
			{
				_zoneName = value;
				break;
			}
			case "flying":
			{
				_IsFlyingEnable = Boolean.parseBoolean(value);
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
		if (character instanceof L2PcInstance)
		{
			final L2PcInstance player = (L2PcInstance) character;
			if (!player.isGM() && player.isFlying() && !player.isInJail() && !_IsFlyingEnable)
			{
				player.teleToLocation(com.l2jmobius.gameserver.datatables.csv.MapRegionTable.TeleportWhereType.Town);
			}
			
			if (_zoneName.equalsIgnoreCase("tradeoff"))
			{
				player.sendMessage("Trade restrictions are involved.");
				player.setTradeDisabled(true);
			}
		}
	}
	
	@Override
	protected void onExit(L2Character character)
	{
		if (character instanceof L2PcInstance)
		{
			final L2PcInstance player = (L2PcInstance) character;
			
			if (_zoneName.equalsIgnoreCase("tradeoff"))
			{
				player.sendMessage("Trade restrictions removed.");
				player.setTradeDisabled(false);
			}
		}
	}
	
	public String getZoneName()
	{
		return _zoneName;
	}
	
	public boolean isFlyingEnable()
	{
		return _IsFlyingEnable;
	}
	
	private String _zoneName;
	private boolean _IsFlyingEnable;
}