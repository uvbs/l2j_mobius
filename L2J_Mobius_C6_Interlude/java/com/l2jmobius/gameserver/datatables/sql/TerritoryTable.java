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
package com.l2jmobius.gameserver.datatables.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.l2jmobius.commons.database.DatabaseFactory;
import com.l2jmobius.gameserver.TradeController;
import com.l2jmobius.gameserver.model.L2Territory;

public class TerritoryTable
{
	private static final Logger LOGGER = Logger.getLogger(TradeController.class.getName());
	private static Map<Integer, L2Territory> _territory = new HashMap<>();
	
	public static TerritoryTable getInstance()
	{
		return SingletonHolder._instance;
	}
	
	public TerritoryTable()
	{
		_territory.clear();
		// load all data at server start
		reload_data();
	}
	
	public int[] getRandomPoint(Integer terr)
	{
		return _territory.get(terr).getRandomPoint();
	}
	
	public int getProcMax(Integer terr)
	{
		return _territory.get(terr).getProcMax();
	}
	
	public void reload_data()
	{
		_territory.clear();
		try (Connection con = DatabaseFactory.getInstance().getConnection())
		{
			final PreparedStatement statement = con.prepareStatement("SELECT loc_id, loc_x, loc_y, loc_zmin, loc_zmax, proc FROM `locations`");
			final ResultSet rset = statement.executeQuery();
			
			while (rset.next())
			{
				// final String terr = "sql_terr_" + rset.getString("loc_id");
				final int terr = rset.getInt("loc_id");
				
				if (_territory.get(terr) == null)
				{
					final L2Territory t = new L2Territory();
					_territory.put(terr, t);
				}
				_territory.get(terr).add(rset.getInt("loc_x"), rset.getInt("loc_y"), rset.getInt("loc_zmin"), rset.getInt("loc_zmax"), rset.getInt("proc"));
			}
			
			rset.close();
			statement.close();
		}
		catch (Exception e1)
		{
			LOGGER.warning("Locations couldn't be initialized " + e1);
		}
		
		LOGGER.info("TerritoryTable: Loaded " + _territory.size() + " locations.");
	}
	
	private static class SingletonHolder
	{
		protected static final TerritoryTable _instance = new TerritoryTable();
	}
}