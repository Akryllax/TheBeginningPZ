package net.lofers.scenario;

import java.lang.reflect.*;
import zombie.characters.IsoPlayer;
import zombie.network.fields.character.PlayerVariables;

/** Populate the stock movement field without changing the Actor's native action state.
 * All reflected classes are pinned by BuildGuard; wire encoding remains the engine's. */
final class ActorMovementVariables {
    private static final Field count,variables;
    private static final Method set;
    private static final Object injury,speed;
    static {
        try {
            count=PlayerVariables.class.getDeclaredField("count");
            variables=PlayerVariables.class.getDeclaredField("variables");
            Class<?> entry=variables.getType().getComponentType();
            Class<?> ids=Class.forName("zombie.network.fields.character.PlayerVariables$NetworkPlayerVariableIDs");
            if(count.getType()!=byte.class||!ids.isEnum()||entry==null)throw new IllegalStateException("movement_variables_contract");
            injury=java.util.Arrays.stream(ids.getEnumConstants()).filter(v->((Enum<?>)v).name().equals("WalkInjury")).findFirst().orElseThrow();
            speed=java.util.Arrays.stream(ids.getEnumConstants()).filter(v->((Enum<?>)v).name().equals("WalkSpeed")).findFirst().orElseThrow();
            set=entry.getDeclaredMethod("set",IsoPlayer.class,ids);
            count.setAccessible(true);variables.setAccessible(true);set.setAccessible(true);
        }catch(ReflectiveOperationException e){throw new ExceptionInInitializerError(e);}
    }
    static void fill(PlayerVariables target,IsoPlayer body,boolean moving) {
        if(!moving){target.set(body);return;}
        try {
            Object[] entries=(Object[])variables.get(target);
            if(entries.length!=2)throw new IllegalStateException("movement_variables_capacity");
            set.invoke(entries[0],body,injury);set.invoke(entries[1],body,speed);count.setByte(target,(byte)2);
        }catch(ReflectiveOperationException e){throw new IllegalStateException("movement_variables",e);}
    }
}
