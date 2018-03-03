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
import java.util.logging.Logger;

import com.l2jmobius.commons.database.DatabaseFactory;
import com.l2jmobius.gameserver.model.actor.instance.L2PcInstance;
import com.l2jmobius.gameserver.network.SystemMessageId;
import com.l2jmobius.gameserver.network.serverpackets.FriendList;
import com.l2jmobius.gameserver.network.serverpackets.SystemMessage;

/**
 * sample 5F 01 00 00 00 format cdd
 */
public final class RequestAnswerFriendInvite extends L2GameClientPacket
{
	private static Logger LOGGER = Logger.getLogger(RequestAnswerFriendInvite.class.getName());
	
	private int _response;
	
	@Override
	protected void readImpl()
	{
		_response = readD();
	}
	
	@Override
	protected void runImpl()
	{
		final L2PcInstance player = getClient().getActiveChar();
		if (player != null)
		{
			final L2PcInstance requestor = player.getActiveRequester();
			if (requestor == null)
			{
				return;
			}
			
			if (_response == 1)
			{
				try (Connection con = DatabaseFactory.getInstance().getConnection())
				{
					PreparedStatement statement = con.prepareStatement("INSERT INTO character_friends (char_id, friend_id) VALUES (?,?), (?,?)");
					statement.setInt(1, requestor.getObjectId());
					statement.setInt(2, player.getObjectId());
					statement.setInt(3, player.getObjectId());
					statement.setInt(4, requestor.getObjectId());
					statement.execute();
					statement.close();
					
					requestor.sendPacket(SystemMessageId.YOU_HAVE_SUCCEEDED_INVITING_FRIEND);
					
					// Player added to your friendlist
					requestor.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_ADDED_TO_FRIENDS).addString(player.getName()));
					requestor.getFriendList().add(player.getObjectId());
					
					// has joined as friend.
					player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_JOINED_AS_FRIEND).addString(requestor.getName()));
					player.getFriendList().add(requestor.getObjectId());
					
					// update friendLists *heavy method*
					requestor.sendPacket(new FriendList(requestor));
					player.sendPacket(new FriendList(player));
				}
				catch (Exception e)
				{
					LOGGER.warning("could not add friend objectid: " + e);
				}
			}
			else
			{
				requestor.sendPacket(SystemMessageId.FAILED_TO_INVITE_A_FRIEND);
			}
			
			player.setActiveRequester(null);
			requestor.onTransactionResponse();
		}
	}
}