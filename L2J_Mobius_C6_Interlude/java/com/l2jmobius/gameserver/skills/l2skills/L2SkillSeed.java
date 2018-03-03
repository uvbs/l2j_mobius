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
package com.l2jmobius.gameserver.skills.l2skills;

import com.l2jmobius.gameserver.model.L2Effect;
import com.l2jmobius.gameserver.model.L2Object;
import com.l2jmobius.gameserver.model.L2Skill;
import com.l2jmobius.gameserver.model.actor.L2Character;
import com.l2jmobius.gameserver.skills.effects.EffectSeed;
import com.l2jmobius.gameserver.templates.StatsSet;

public class L2SkillSeed extends L2Skill
{
	
	public L2SkillSeed(StatsSet set)
	{
		super(set);
	}
	
	@Override
	public void useSkill(L2Character caster, L2Object[] targets)
	{
		if (caster.isAlikeDead())
		{
			return;
		}
		
		// Update Seeds Effects
		for (L2Object target2 : targets)
		{
			final L2Character target = (L2Character) target2;
			if (target.isAlikeDead() && (getTargetType() != SkillTargetType.TARGET_CORPSE_MOB))
			{
				continue;
			}
			
			final EffectSeed oldEffect = (EffectSeed) target.getFirstEffect(getId());
			if (oldEffect == null)
			{
				getEffects(caster, target, false, false, false);
			}
			else
			{
				oldEffect.increasePower();
			}
			
			final L2Effect[] effects = target.getAllEffects();
			for (L2Effect effect : effects)
			{
				if (effect.getEffectType() == L2Effect.EffectType.SEED)
				{
					effect.rescheduleEffect();
					/*
					 * for (int j=0;j<effects.length;j++ { if (effects[j].getEffectType()==L2Effect.EffectType.SEED) { EffectSeed e = (EffectSeed)effects[j]; if (e.getInUse() || e.getSkill().getId()==this.getId()) { e.rescheduleEffect(); } } }
					 */
				}
			}
		}
	}
}