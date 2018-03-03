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
package com.l2jmobius.gameserver.network.clientpackets;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Logger;

import com.l2jmobius.commons.database.DatabaseFactory;
import com.l2jmobius.gameserver.cache.CrestCache;
import com.l2jmobius.gameserver.idfactory.IdFactory;
import com.l2jmobius.gameserver.model.L2Clan;
import com.l2jmobius.gameserver.model.actor.instance.L2PcInstance;
import com.l2jmobius.gameserver.network.SystemMessageId;

/**
 * Format : chdb c (id) 0xD0 h (subid) 0x11 d data size b raw data (picture i think ;) )
 * @author -Wooden-
 */
public final class RequestExSetPledgeCrestLarge extends L2GameClientPacket
{
	static Logger LOGGER = Logger.getLogger(RequestExSetPledgeCrestLarge.class.getName());
	private int _size;
	private byte[] _data;
	
	@Override
	protected void readImpl()
	{
		_size = readD();
		
		if (_size > 2176)
		{
			return;
		}
		
		if (_size > 0) // client CAN send a RequestExSetPledgeCrestLarge with the size set to 0 then format is just chd
		{
			_data = new byte[_size];
			readB(_data);
		}
	}
	
	@Override
	protected void runImpl()
	{
		final L2PcInstance activeChar = getClient().getActiveChar();
		
		if (activeChar == null)
		{
			return;
		}
		
		final L2Clan clan = activeChar.getClan();
		if (clan == null)
		{
			return;
		}
		
		if (_data == null)
		{
			CrestCache.getInstance().removePledgeCrestLarge(clan.getCrestId());
			
			clan.setHasCrestLarge(false);
			activeChar.sendMessage("The insignia has been removed.");
			
			for (L2PcInstance member : clan.getOnlineMembers(""))
			{
				member.broadcastUserInfo();
			}
			
			return;
		}
		
		if (_size > 2176)
		{
			activeChar.sendMessage("The insignia file size is greater than 2176 bytes.");
			return;
		}
		
		if ((activeChar.getClanPrivileges() & L2Clan.CP_CL_REGISTER_CREST) == L2Clan.CP_CL_REGISTER_CREST)
		{
			if ((clan.getHasCastle() == 0) && (clan.getHasHideout() == 0))
			{
				activeChar.sendMessage("Only a clan that owns a clan hall or a castle can get their emblem displayed on clan related items"); // there is a system message for that but didnt found the id
				return;
			}
			
			final CrestCache crestCache = CrestCache.getInstance();
			
			final int newId = IdFactory.getInstance().getNextId();
			
			if (!crestCache.savePledgeCrestLarge(newId, _data))
			{
				LOGGER.warning("Error loading large crest of clan:" + clan.getName());
				return;
			}
			
			if (clan.hasCrestLarge())
			{
				crestCache.removePledgeCrestLarge(clan.getCrestLargeId());
			}
			
			try (Connection con = DatabaseFactory.getInstance().getConnection())
			{
				PreparedStatement statement = con.prepareStatement("UPDATE clan_data SET crest_large_id = ? WHERE clan_id = ?");
				statement.setInt(1, newId);
				statement.setInt(2, clan.getClanId());
				statement.executeUpdate();
				statement.close();
			}
			catch (SQLException e)
			{
				LOGGER.warning("could not update the large crest id:" + e.getMessage());
			}
			
			clan.setCrestLargeId(newId);
			clan.setHasCrestLarge(true);
			
			activeChar.sendPacket(SystemMessageId.CLAN_EMBLEM_WAS_SUCCESSFULLY_REGISTERED);
			
			for (L2PcInstance member : clan.getOnlineMembers(""))
			{
				member.broadcastUserInfo();
			}
		}
	}
}