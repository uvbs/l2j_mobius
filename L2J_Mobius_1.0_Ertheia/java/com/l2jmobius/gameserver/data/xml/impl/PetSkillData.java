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
package com.l2jmobius.gameserver.data.xml.impl;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import com.l2jmobius.commons.util.IGameXmlReader;
import com.l2jmobius.gameserver.model.actor.L2Summon;

/**
 * @author Mobius
 */
public class PetSkillData implements IGameXmlReader
{
	private static Logger LOGGER = Logger.getLogger(PetSkillData.class.getName());
	private final Map<Integer, Map<Long, L2PetSkillLearn>> _skillTrees = new HashMap<>();
	
	protected PetSkillData()
	{
		load();
	}
	
	@Override
	public void load()
	{
		_skillTrees.clear();
		parseDatapackFile("data/PetSkillData.xml");
		LOGGER.info(getClass().getSimpleName() + ": Loaded " + _skillTrees.size() + " skills.");
	}
	
	@Override
	public void parseDocument(Document doc, File f)
	{
		for (Node n = doc.getFirstChild(); n != null; n = n.getNextSibling())
		{
			if ("list".equalsIgnoreCase(n.getNodeName()))
			{
				for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling())
				{
					if ("skill".equalsIgnoreCase(d.getNodeName()))
					{
						final NamedNodeMap attrs = d.getAttributes();
						
						final int npcId = parseInteger(attrs, "templateId");
						final int id = parseInteger(attrs, "skillId");
						final int lvl = parseInteger(attrs, "skillLvl");
						final int minLvl = parseInteger(attrs, "minLvl");
						
						Map<Long, L2PetSkillLearn> skillTree = _skillTrees.get(npcId);
						if (skillTree == null)
						{
							skillTree = new HashMap<>();
							_skillTrees.put(npcId, skillTree);
						}
						
						if (SkillData.getInstance().getSkill(id, lvl == 0 ? 1 : lvl) != null)
						{
							skillTree.put(SkillData.getSkillHashCode(id, lvl + 1), new L2PetSkillLearn(id, lvl, minLvl));
						}
						else
						{
							LOGGER.info(getClass().getSimpleName() + ": Could not find skill with id " + id + ", level " + lvl + " for NPC " + npcId + ".");
						}
					}
				}
			}
		}
	}
	
	public int getAvailableLevel(L2Summon cha, int skillId)
	{
		int lvl = 0;
		if (!_skillTrees.containsKey(cha.getId()))
		{
			LOGGER.warning(getClass().getSimpleName() + ": Pet id " + cha.getId() + " does not have any skills assigned.");
			return lvl;
		}
		final Collection<L2PetSkillLearn> skills = _skillTrees.get(cha.getId()).values();
		for (L2PetSkillLearn temp : skills)
		{
			if (temp.getId() != skillId)
			{
				continue;
			}
			if (temp.getLevel() == 0)
			{
				if (cha.getLevel() < 70)
				{
					lvl = cha.getLevel() / 10;
					if (lvl <= 0)
					{
						lvl = 1;
					}
				}
				else
				{
					lvl = 7 + ((cha.getLevel() - 70) / 5);
				}
				
				// formula usable for skill that have 10 or more skill levels
				final int maxLvl = SkillData.getInstance().getMaxLevel(temp.getId());
				if (lvl > maxLvl)
				{
					lvl = maxLvl;
				}
				break;
			}
			else if (temp.getMinLevel() <= cha.getLevel())
			{
				if (temp.getLevel() > lvl)
				{
					lvl = temp.getLevel();
				}
			}
		}
		return lvl;
	}
	
	public List<Integer> getAvailableSkills(L2Summon cha)
	{
		final List<Integer> skillIds = new ArrayList<>();
		if (!_skillTrees.containsKey(cha.getId()))
		{
			LOGGER.warning(getClass().getSimpleName() + ": Pet id " + cha.getId() + " does not have any skills assigned.");
			return skillIds;
		}
		final Collection<L2PetSkillLearn> skills = _skillTrees.get(cha.getId()).values();
		for (L2PetSkillLearn temp : skills)
		{
			if (skillIds.contains(temp.getId()))
			{
				continue;
			}
			skillIds.add(temp.getId());
		}
		return skillIds;
	}
	
	public static final class L2PetSkillLearn
	{
		private final int _id;
		private final int _level;
		private final int _minLevel;
		
		public L2PetSkillLearn(int id, int lvl, int minLvl)
		{
			_id = id;
			_level = lvl;
			_minLevel = minLvl;
		}
		
		public int getId()
		{
			return _id;
		}
		
		public int getLevel()
		{
			return _level;
		}
		
		public int getMinLevel()
		{
			return _minLevel;
		}
	}
	
	public static PetSkillData getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private static class SingletonHolder
	{
		protected static final PetSkillData _instance = new PetSkillData();
	}
}