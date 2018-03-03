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
package com.l2jmobius.gameserver.model.actor.instance;

import com.l2jmobius.gameserver.templates.chars.L2NpcTemplate;

/**
 * This class ...
 * @version $Revision: 1.5.4.8 $ $Date: 2005/04/02 15:57:52 $
 */
public final class L2TrainerInstance extends L2FolkInstance
{
	/**
	 * Instantiates a new l2 trainer instance.
	 * @param objectId the object id
	 * @param template the template
	 */
	public L2TrainerInstance(int objectId, L2NpcTemplate template)
	{
		super(objectId, template);
	}
	
	/*
	 * (non-Javadoc)
	 * @see com.l2jmobius.gameserver.model.actor.instance.L2NpcInstance#getHtmlPath(int, int)
	 */
	@Override
	public String getHtmlPath(int npcId, int val)
	{
		String pom = "";
		if (val == 0)
		{
			pom = "" + npcId;
		}
		else
		{
			pom = npcId + "-" + val;
		}
		
		return "data/html/trainer/" + pom + ".htm";
	}
}