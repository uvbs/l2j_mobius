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
package quests.Q319_ScentOfDeath;

import com.l2jmobius.gameserver.model.actor.instance.L2NpcInstance;
import com.l2jmobius.gameserver.model.actor.instance.L2PcInstance;
import com.l2jmobius.gameserver.model.quest.Quest;
import com.l2jmobius.gameserver.model.quest.QuestState;
import com.l2jmobius.gameserver.model.quest.State;

public class Q319_ScentOfDeath extends Quest
{
	private static final String qn = "Q319_ScentOfDeath";
	
	// Item
	private static final int ZOMBIE_SKIN = 1045;
	
	public Q319_ScentOfDeath()
	{
		super(319, qn, "Scent of Death");
		
		registerQuestItems(ZOMBIE_SKIN);
		
		addStartNpc(30138); // Minaless
		addTalkId(30138);
		
		addKillId(20015, 20020);
	}
	
	@Override
	public String onAdvEvent(String event, L2NpcInstance npc, L2PcInstance player)
	{
		String htmltext = event;
		QuestState st = player.getQuestState(qn);
		if (st == null)
		{
			return htmltext;
		}
		
		if (event.equals("30138-04.htm"))
		{
			st.setState(State.STARTED);
			st.set("cond", "1");
			st.playSound(QuestState.SOUND_ACCEPT);
		}
		
		return htmltext;
	}
	
	@Override
	public String onTalk(L2NpcInstance npc, L2PcInstance player)
	{
		QuestState st = player.getQuestState(qn);
		String htmltext = getNoQuestMsg();
		if (st == null)
		{
			return htmltext;
		}
		
		switch (st.getState())
		{
			case State.CREATED:
				htmltext = (player.getLevel() < 11) ? "30138-02.htm" : "30138-03.htm";
				break;
			
			case State.STARTED:
				if (st.getInt("cond") == 1)
				{
					htmltext = "30138-05.htm";
				}
				else
				{
					htmltext = "30138-06.htm";
					st.takeItems(ZOMBIE_SKIN, -1);
					st.rewardItems(57, 3350);
					st.rewardItems(1060, 1);
					st.playSound(QuestState.SOUND_FINISH);
					st.exitQuest(true);
				}
				break;
		}
		return htmltext;
	}
	
	@Override
	public String onKill(L2NpcInstance npc, L2PcInstance player, boolean isPet)
	{
		QuestState st = checkPlayerCondition(player, npc, "cond", "1");
		if (st == null)
		{
			return null;
		}
		
		if (st.dropItems(ZOMBIE_SKIN, 1, 5, 200000))
		{
			st.set("cond", "2");
		}
		
		return null;
	}
}